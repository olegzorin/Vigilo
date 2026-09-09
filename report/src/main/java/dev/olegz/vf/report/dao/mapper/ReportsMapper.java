package dev.olegz.vf.report.dao.mapper;

import java.sql.Timestamp;
import java.util.List;

import org.apache.ibatis.annotations.Param;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportGroup;
import dev.olegz.vf.report.domain.ReportGroupOrganization;
import dev.olegz.vf.report.domain.ReportGroupSchedule;
import dev.olegz.vf.report.domain.ReportMetadata;
import dev.olegz.vf.report.domain.ReportParam;

public interface ReportsMapper {
    int updateReportDefinition(@Param("report") Report report, @Param("sql") String sql);
    void insertReportDefinition(@Param("report") Report report, @Param("sql") String sql);
    void deleteReportParams(int reportId);
    void insertReportParam(ReportParam parameter);
    void deleteReportFields(int reportId);
    void insertReportField(ReportField field);
    void deleteReportMetadata(int reportId);
    void insertReportMetadata(ReportMetadata metadata);

    void insertReportGroupReport(
        @Param("reportId") int reportId,
        @Param("reportGroupId") int reportGroupId,
        @Param("startDate") Timestamp startDate);

    void insertReportSchedule(ReportGroupSchedule schedule);

    void deleteReportSchedule(
        @Param("reportId") int reportId,
        @Param("reportGroupId") int reportGroupId);

    void deleteReportExecutions(int reportId);

    Integer selectReportGroupId(
        @Param("reportId") int reportId,
        @Param("organizationId") Integer organizationId,
        @Param("reportGroupId") Integer reportGroupId);
    Report selectReportForExecution(int reportId);
    List<Report> selectSystemReports(
        @Param("reportId") Integer reportId,
        @Param("reportGroupId") Integer reportGroupId,
        @Param("reportType") Byte reportType);
    List<Report> selectOrganizationReports(
        @Param("organizationId") int organizationId,
        @Param("reportId") Integer reportId,
        @Param("reportGroupId") Integer reportGroupId,
        @Param("reportType") Byte reportType);
    Report selectReportWithFields(
        @Param("reportId") int reportId,
        @Param("reportGroupId") int reportGroupId);
    boolean insertReportGroupOrganization(
        @Param("reportGroupId") int reportGroupId,
        @Param("organizationId") int organizationId,
        @Param("assignedAt") Timestamp assignedAt);
    boolean deleteReportGroupOrganizations(
        @Param("reportGroupId") int reportGroupId,
        @Param("organizationId") int organizationId,
        @Param("excluded") boolean excluded);
    void deleteReportGroupOrganizationsByOrganization(int organizationId);
    ReportGroupOrganization selectReportGroupOrganization(
        @Param("reportGroupId") int reportGroupId,
        @Param("organizationId") int organizationId);
    List<ReportGroupOrganization> selectReportGroupOrganizations();
    List<ReportGroup> selectOrganizationReportGroups(
        @Param("organizationId") int organizationId,
        @Param("analytic") boolean analytic,
        @Param("all") boolean all);

    List<ReportGroupSchedule> selectPastReportSchedules(Timestamp date);
    List<ReportGroupSchedule> selectReportSchedules(int reportId);
    ReportGroupSchedule selectReportScheduleForUpdate(int scheduleId);
    void updateReportGroupScheduleExecutionDates(
        @Param("scheduleId") int scheduleId,
        @Param("lastExecutionDate") Timestamp lastExecutionDate,
        @Param("nextExecutionDate") Timestamp nextExecutionDate);
    void insertReportExecution(ReportExecution reportExecution);
    ReportExecution selectReportExecutionByObjectId(String objectId);
    List<ReportExecution> selectScheduledReportExecutions(
        @Param("reportId") int reportId,
        @Param("reportGroupId") int reportGroupId,
        @Param("analytic") boolean analytic,
        @Param("organizationId") Integer organizationId,
        @Param("startDate") Timestamp startDate,
        @Param("endDate") Timestamp endDate);
    void deleteOnDemandReportExecutions(Timestamp expirationDate);

    List<ReportData> executeQuery(
        @Param("query") String query,
        @Param("p0") Object p0,
        @Param("p1") Object p1,
        @Param("p2") Object p2,
        @Param("p3") Object p3,
        @Param("p4") Object p4,
        @Param("p5") Object p5,
        @Param("p6") Object p6,
        @Param("p7") Object p7,
        @Param("p8") Object p8,
        @Param("p9") Object p9);

}
