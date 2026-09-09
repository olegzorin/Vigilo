package dev.olegz.vf.worker;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.config.CoreConfig;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.report.ReportConfig;
import dev.olegz.vf.core.domain.lambdaversion.Lambda;
import dev.olegz.vf.core.system.ResourceManager;
import dev.olegz.vf.messaging.*;
import dev.olegz.vf.messaging.consumer.ConsumerConfig;
import dev.olegz.vf.worker.listener.*;
import dev.olegz.vf.worker.scheduler.ExecutorCronScheduler;
import dev.olegz.vf.worker.service.LambdaResultQueueProvisioner;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.server.servlet.context.ServletComponentScan;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@ServletComponentScan
@Import({CoreConfig.class, DataSourceConfig.class, ReportConfig.class})
public class Application implements ApplicationListener<ApplicationReadyEvent> {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);
    private static final int DEFAULT_LAMBDA_CODE_POOL_SIZE = 4;

    private static final ResourceManager.ResourceId[] RESOURCES = {
        ResourceManager.ResourceId.STREAM_PRODUCER,
        ResourceManager.ResourceId.RUNTIME
    };

    static void main(String[] args) {
        PropertyStore.start();
        SpringApplication.run(Application.class, args);
    }

    private final LambdaOperationListener lambdaOperationListener;
    private final LambdaErrorListener lambdaErrorListener;
    private final LambdaRunCompletionListener lambdaRunCompletionListener;
    private final LambdaInputListener lambdaInputListener;
    private final LambdaResetListener lambdaResetListener;
    private final LambdaScheduleListener lambdaScheduleListener;
    private final LambdaInvokeRequestListener lambdaInvokeRequestListener;
    private final LambdaAsyncResponseListener lambdaAsyncResponseListener;
    private final ExecutorCronScheduler cronScheduler;

    private static volatile boolean running = true;

    public static boolean isRunning() {
        return running;
    }

    public Application(LambdaOperationListener lambdaOperationListener, LambdaErrorListener lambdaErrorListener,
        LambdaRunCompletionListener lambdaRunCompletionListener, LambdaInputListener lambdaInputListener,
        LambdaResetListener lambdaResetListener,
        LambdaScheduleListener lambdaScheduleListener,
        LambdaInvokeRequestListener lambdaInvokeRequestListener,
        LambdaAsyncResponseListener lambdaAsyncResponseListener,
        ExecutorCronScheduler cronScheduler)
    {
        this.lambdaOperationListener = lambdaOperationListener;
        this.lambdaErrorListener = lambdaErrorListener;
        this.lambdaRunCompletionListener = lambdaRunCompletionListener;
        this.lambdaInputListener = lambdaInputListener;
        this.lambdaResetListener = lambdaResetListener;
        this.lambdaScheduleListener = lambdaScheduleListener;
        this.lambdaInvokeRequestListener = lambdaInvokeRequestListener;
        this.lambdaAsyncResponseListener = lambdaAsyncResponseListener;
        this.cronScheduler = cronScheduler;
    }

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        logger.warn("Application starting");

        ResourceManager.open(RESOURCES);
        init();
        cronScheduler.start();

        logger.warn("Application started");
    }

    private void init() {
        logger.warn("Application initialization started");
        LambdaResultQueueProvisioner.provision();
        ScopedMessageBroker kafka = Messaging.broker(MessagingProvider.KAFKA, ScopedMessageBroker.class);
        MessageBroker sqs = Messaging.broker(MessagingProvider.SQS);
        registerStreamingListeners(kafka, sqs);
        logger.warn("Application streaming listeners started");
    }

    void registerStreamingListeners(ScopedMessageBroker kafka, MessageBroker sqs) {
        // General async request processors
        kafka.setMessageListeners(Topics.OPERATIONS, lambdaOperationListener,
            ConsumerConfig.concurrent(10).withMaxPollRecords(5));

        // Lambda code uploads are processed concurrently without blocking the Kafka polling thread.
        int lambdaCodePoolSize = PropertyStore.getInt("dsp.lambdaCode.poolSize", DEFAULT_LAMBDA_CODE_POOL_SIZE);
        if (lambdaCodePoolSize <= 0) {
            logger.error("Invalid dsp.lambdaCode.poolSize={}; using default={}",
                lambdaCodePoolSize, DEFAULT_LAMBDA_CODE_POOL_SIZE);
            lambdaCodePoolSize = DEFAULT_LAMBDA_CODE_POOL_SIZE;
        }
        int maxPollInterval = PropertyStore.getInt("dsp.lambdaCode.pollInterval", 300);
        kafka.setMessageListeners(Topics.LAMBDA_CODE_UPLOAD, lambdaOperationListener,
            ConsumerConfig.concurrent(lambdaCodePoolSize).withMaxPollInterval(maxPollInterval)
                .withMaxPollRecords(lambdaCodePoolSize));

        // Lambda run errors, consumed from the lambda-error topic (oversized messages are truncated by
        // the producer, so all errors arrive here).
        kafka.setMessageListener(Topics.LAMBDA_ERROR, lambdaErrorListener, maxPollInterval, 5);

        // Lambda input processing
        kafka.setMessageListeners(Topics.LAMBDA_INPUT, lambdaInputListener,
            ConsumerConfig.ordered(30).withMaxPollRecords(5));

        // Assignment lifecycle resets are dispatched only to their specific lambda assignment
        kafka.setMessageListeners(Topics.LAMBDA_RESET, lambdaResetListener,
            ConsumerConfig.ordered(30).withMaxPollRecords(5));

        // Scheduled events are durable in Kafka and deduplicated by their stable event identity
        kafka.setMessageListeners(Topics.LAMBDA_SCHEDULE, lambdaScheduleListener,
            ConsumerConfig.ordered(30).withMaxPollRecords(5));

        // Lambda engine - process requests concurrently and acknowledge each only after its Lambda attempt completes.
        kafka.setMessageListeners(Topics.LAMBDA_INVOKE_REQUEST, lambdaInvokeRequestListener,
            ConsumerConfig.concurrent(10).withMaxPollRecords(10));

        // Default-lane lambda run completion processing
        kafka.setMessageListeners(Topics.LAMBDA_RUN_COMPLETION, lambdaRunCompletionListener,
            ConsumerConfig.ordered(10).withMaxPollRecords(10));

        int asyncResponsePollInterval = PropertyStore.getInt("vf.lambda.asyncResponse.pollInterval", 60);
        int asyncResponseMaxRecords = PropertyStore.getInt("vf.lambda.asyncResponse.maxRecords", 10);
        sqs.setMessageListener(Lambda.SQS_RESULT_QUEUE,
            lambdaAsyncResponseListener, asyncResponsePollInterval, asyncResponseMaxRecords);
    }

    @PreDestroy
    public void destroy() {
        running = false;
        logger.warn("Application stopping");

        try {
            ResourceManager.close(RESOURCES);
        } catch (Exception e) {
            logger.error("Exception in closing resources", e);
        }

        try {
            cronScheduler.shutdown();
        } catch (Exception e) {
            logger.error("Exception in scheduler shutdown", e);
        }

        logger.warn("Application stopped");
    }
}
