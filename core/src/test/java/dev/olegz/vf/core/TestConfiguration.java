package dev.olegz.vf.core;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.config.CoreConfig;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.registry.config.DataSourceConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Test context for the lambda-platform core module. */
@Configuration
@Import({CoreConfig.class, DataSourceConfig.class})
public class TestConfiguration {
    static {
        PropertyStore.set("vf.aws.local", "true");
        PropertyStore.start();
        Messaging.disable();
    }
}
