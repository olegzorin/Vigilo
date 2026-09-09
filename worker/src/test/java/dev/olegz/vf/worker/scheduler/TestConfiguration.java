package dev.olegz.vf.worker.scheduler;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.core.config.CoreConfig;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.messaging.Messaging;
import dev.olegz.vf.worker.Application;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({Application.class, CoreConfig.class, DataSourceConfig.class})
public class TestConfiguration {
    static {
        PropertyStore.set("vf.aws.local", "true");
        PropertyStore.start();
        Messaging.disable();
    }
}
