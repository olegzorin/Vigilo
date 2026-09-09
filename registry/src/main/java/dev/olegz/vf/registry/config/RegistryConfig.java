package dev.olegz.vf.registry.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Spring component configuration for tenant registry and user authorization services. */
@Configuration
@ComponentScan(basePackages = {
    "dev.olegz.vf.registry.dao.impl",
    "dev.olegz.vf.registry.service"
})
public class RegistryConfig {
}
