package dev.olegz.vf.report.rest;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/** Activates the optional HTTP adapter separately from the report service and persistence layer. */
@Configuration
@ComponentScan("dev.olegz.vf.report.rest")
public class ReportRestConfig {
}
