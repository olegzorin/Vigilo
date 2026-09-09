package dev.olegz.vf.report.service;

import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportGroup;

public interface ReportsService {
    Report getReport(int reportId);

    List<Report> getReports(Integer reportId, Integer organizationId, Integer reportGroupId, Boolean analytic);

    List<ReportGroup> getOrganizationReportGroups(int organizationId, boolean analytic, boolean all);

    void setReportGroupOrganization(int reportGroupId, int organizationId);

    void deleteReportGroupOrganization(int reportGroupId, int organizationId);

    Integer getReportGroupId(int reportId, Integer organizationId);

    Report getScheduledReportExecutions(int reportId, int reportGroupId, Integer organizationId,
        Timestamp startDate, Timestamp endDate);

    List<Report.Dictionary> getReportDictionary(Report report);

    ReportExecution executeOnDemand(int reportId, Integer organizationId, Map<String, String> parameters, ZoneId zoneId);

    ReportExecution getReportExecution(String objectId);

    int runScheduledReports();

    void deleteExpiredExecutions();
}
