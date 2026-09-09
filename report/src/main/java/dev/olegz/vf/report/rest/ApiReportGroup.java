package dev.olegz.vf.report.rest;

import dev.olegz.vf.report.domain.ReportGroup;

public class ApiReportGroup {
    public final int reportGroupId;
    public final String name;
    public final String description;
    public final boolean analytic;

    public ApiReportGroup(ReportGroup group) {
        reportGroupId = group.reportGroupId;
        name = group.name;
        description = group.description;
        analytic = group.analytic;
    }
}
