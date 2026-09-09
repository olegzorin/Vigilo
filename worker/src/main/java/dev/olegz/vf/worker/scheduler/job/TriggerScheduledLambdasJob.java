package dev.olegz.vf.worker.scheduler.job;

import dev.olegz.vf.common.objectmap.BytesMapper;
import dev.olegz.vf.core.event.ScheduledEvent;
import dev.olegz.vf.core.service.lambda.LambdaClientService;
import dev.olegz.vf.messaging.ConfirmingMessageProducer;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.messaging.MessagingProvider;
import dev.olegz.vf.messaging.Topics;
import dev.olegz.vf.worker.scheduler.CronJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class TriggerScheduledLambdasJob implements CronJob {
    private static final Logger logger = LoggerFactory.getLogger(TriggerScheduledLambdasJob.class);

    private final LambdaClientService lambdaClientService;
    private final ConfirmingMessageProducer producer;

    @Autowired
    public TriggerScheduledLambdasJob(LambdaClientService lambdaClientService) {
        this(lambdaClientService,
            Messaging.producer(MessagingProvider.KAFKA, "lambdaSchedule", ConfirmingMessageProducer.class));
    }

    TriggerScheduledLambdasJob(LambdaClientService lambdaClientService, ConfirmingMessageProducer producer) {
        this.lambdaClientService = lambdaClientService;
        this.producer = producer;
    }

    @Override
    public String name() {
        return "ScheduleLambdas";
    }

    @Override
    public String cron() {
        return "30 0 * * * ?";
    }

    @Override
    public void run() {
        try {
            lambdaClientService.triggerScheduledLambdas(this::publish);
        } catch (Exception e) {
            logger.error("Exception in running ScheduleLambdas job", e);
        }
    }

    void publish(ScheduledEvent event) {
        producer.sendAndAwait(Topics.LAMBDA_SCHEDULE, BytesMapper.writeValue(event));
    }

}
