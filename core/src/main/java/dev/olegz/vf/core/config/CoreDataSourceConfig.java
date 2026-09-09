package dev.olegz.vf.core.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/** Registers the lambda-platform mappers with the registry-owned SQL session. */
@Configuration
@MapperScan(basePackages = "dev.olegz.vf.core.dao.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class CoreDataSourceConfig {
}
