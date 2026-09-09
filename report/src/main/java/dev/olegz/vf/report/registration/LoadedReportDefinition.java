package dev.olegz.vf.report.registration;

import java.nio.file.Path;

import dev.olegz.vf.report.domain.Report;

/** A validated report definition together with its executable SQL. */
public record LoadedReportDefinition(Report report, Path source, String sql) {
}
