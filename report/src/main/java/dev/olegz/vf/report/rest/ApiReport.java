package dev.olegz.vf.report.rest;

import java.util.List;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportParam;

public class ApiReport {
    public final int reportId;
    public final String name;
    public final String displayName;
    public final String description;
    public final boolean analytic;
    public final List<ApiReportParam> parameters;

    public ApiReport(Report report) {
        reportId = report.reportId;
        name = report.reportName;
        displayName = report.displayName;
        description = report.description;
        analytic = report.isAnalytic();
        List<ReportParam> params = report.getParamsOrdered();
        parameters = params == null ? null : params.stream().map(ApiReportParam::new).toList();
    }

    public static class ApiReportParam {
        public final String name;
        public final int dataType;
        public final boolean required;
        public final String displayName;
        public final String description;
        public final String placeholder;

        ApiReportParam(ReportParam parameter) {
            name = parameter.name;
            dataType = parameter.dataType;
            required = parameter.required;
            displayName = parameter.displayName;
            description = parameter.description;
            placeholder = parameter.placeholder;
        }
    }
}
