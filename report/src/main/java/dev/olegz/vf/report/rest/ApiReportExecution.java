package dev.olegz.vf.report.rest;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.output.AnalyticReportOutput;

public class ApiReportExecution {
    public final int reportId;
    public final String reportName;
    public final Integer scheduleId;
    public final Timestamp executionDate;
    public final Timestamp scheduleDate;
    public final Integer organizationId;
    public final int executionTime;
    public final int rowCount;
    public final Map<String, String> metadata;
    public final String errorMessage;
    public final String url;
    public final String urlJson;
    public final List<AnalyticReportOutput.Item> data;

    public ApiReportExecution(ReportExecution execution, String url, AnalyticReportOutput analyticOutput) {
        reportId = execution.reportId;
        reportName = execution.reportName;
        scheduleId = execution.scheduleId;
        executionDate = execution.executionDate;
        scheduleDate = execution.scheduleDate;
        organizationId = execution.organizationId;
        executionTime = execution.executionTime;
        rowCount = execution.rowCount;
        metadata = execution.metadata;
        errorMessage = execution.errorMessage;
        this.url = errorMessage == null ? url : null;
        urlJson = this.url == null ? null : this.url + "?responseFormat=" + ReportDataAction.RESPONSE_FORMAT_JSON;
        data = analyticOutput == null ? null : analyticOutput.items;
    }
}
