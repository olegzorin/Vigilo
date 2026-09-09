package dev.olegz.vf.report.dao;

import java.sql.Timestamp;
import java.util.List;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportGroup;
import dev.olegz.vf.report.domain.ReportGroupOrganization;
import dev.olegz.vf.report.domain.ReportGroupSchedule;

public interface ReportsDao {

    void insertReportGroupReport(int reportId, int reportGroupId);

    void insertReportGroupSchedule(ReportGroupSchedule schedule);

    void deleteReportGroupSchedule(int reportId, int reportGroupId);

    void deleteReportExecutions(int reportId);

    void updateReportGroupScheduleExecutionDates(int scheduleId, Timestamp lastExecutionDate, Timestamp nextExecutionDate);

    Integer getReportGroupId(int reportId, Integer organizationId, Integer reportGroupId);

    List<Report> getReports(Integer reportId, Integer reportGroupId, Boolean analytic, Integer organizationId);

    Report getReportForExecution(int reportId);

    Report getReportWithFields(int reportId, int reportGroupId);

    List<ReportGroupSchedule> getReportSchedules();

    List<ReportGroupSchedule> getReportSchedules(int reportId);

    ReportGroupSchedule getReportScheduleForUpdate(int scheduleId);

    void insertReportExecution(ReportExecution reportExecution);

    void insertReportGroupOrganization(int reportGroupId, int organizationId);

    boolean deleteReportGroupOrganization(int reportGroupId, int organizationId);

    void deleteReportGroupOrganizationsByOrganization(int organizationId);

    ReportGroupOrganization getReportGroupOrganization(int organizationId, int reportGroupId);

    List<ReportGroupOrganization> getReportGroupOrganizations();

    List<ReportGroup> getOrganizationReportGroups(int organizationId, boolean analytic, boolean all);

    List<ReportExecution> getScheduledReportExecutions(int reportId, int reportGroupId, boolean analytic, Integer organizationId, Timestamp startDate, Timestamp endDate);

    ReportExecution getReportExecution(String objectId);

    void deleteOnDemandReportExecutions(Timestamp expirationDate);

    List<ReportData> executeQuery(String query, Object[] params);

}
