package dev.olegz.vf.core.config;

import dev.olegz.vf.aws.client.AwsClients;
import dev.olegz.vf.aws.cloudwatch.CloudWatchLogsSupport;
import dev.olegz.vf.aws.ecr.EcrPublicSupport;
import dev.olegz.vf.aws.ecr.EcrSupport;
import dev.olegz.vf.aws.s3.S3Support;
import dev.olegz.vf.aws.sns.SnsSupport;
import dev.olegz.vf.aws.sqs.SqsSupport;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Closes the long-lived AWS clients held by the static support classes when a Spring context
 * shuts down. Lives in {@code core} (which has Spring) so the {@code aws} module stays
 * Spring-free; it only delegates to the idempotent {@code shutdown()} methods.
 * <p>
 * Registered as a {@code @Bean} in {@link CoreConfig}, so every app importing the core config
 * (worker or api) closes the clients on context shutdown.
 */
public class AwsClientLifecycle {
    private static final Logger logger = LoggerFactory.getLogger(AwsClientLifecycle.class);

    @PreDestroy
    public void shutdown() {
        S3Support.shutdown();
        SqsSupport.shutdown();
        SnsSupport.shutdown();
        CloudWatchLogsSupport.shutdown();
        EcrSupport.shutdown();
        EcrPublicSupport.shutdown();
        AwsClients.closeHttpClient();
        logger.info("AWS clients closed on context shutdown");
    }
}
