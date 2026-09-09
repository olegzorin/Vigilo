package dev.olegz.vf.registry;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.registry.config.RegistryConfig;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Test context for the registry module.
 */
@Configuration
@Import({RegistryConfig.class, DataSourceConfig.class})
public class TestConfiguration {
    static {
        PropertyStore.start();
    }
}
