package dev.olegz.vf.report;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import dev.olegz.vf.report.dao.impl.ReportsDaoImpl;
import dev.olegz.vf.report.service.ReportsServiceImpl;
import dev.olegz.vf.report.storage.S3ReportOutputStore;

/** Spring wiring for the independently consumable report module. */
@Configuration
@Import({ReportExecutor.class, ReportsDaoImpl.class, ReportsServiceImpl.class, S3ReportOutputStore.class})
@MapperScan(basePackages = "dev.olegz.vf.report.dao.mapper", sqlSessionTemplateRef = "sqlSessionTemplate")
public class ReportConfig {
}
