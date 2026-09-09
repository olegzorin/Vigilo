package dev.olegz.vf.api;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.config.CoreConfig;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.messaging.Messaging;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Test context for the RESTful (lambda developer) API module.
 * Wires the same beans as the running application - core Spring config + datasource via
 * {@link CoreConfig}/{@link DataSourceConfig} - plus the API controllers/actions and
 * the AWS stubs, with data streaming disabled so no Kafka/SQS connection is attempted.
 */
@Configuration
@Import({CoreConfig.class, DataSourceConfig.class})
@ComponentScan(basePackages = {
    "dev.olegz.vf.api.account",
    "dev.olegz.vf.api.lambda",
    "dev.olegz.vf.api.device",
    "dev.olegz.vf.api.location",
    "dev.olegz.vf.api.organization",
    "dev.olegz.vf.api.team",
    "dev.olegz.vf.api.web.support"
})
public class TestConfiguration {
    static {
        PropertyStore.set("vf.aws.local", "true");
        PropertyStore.start();
        Messaging.disable();
    }
}
