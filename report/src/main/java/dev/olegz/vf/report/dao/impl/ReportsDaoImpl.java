package dev.olegz.vf.report.dao.impl;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import dev.olegz.vf.common.exception.ObjectNotFoundException;
import org.apache.commons.lang3.StringUtils;
import dev.olegz.vf.report.dao.ReportsDao;
import dev.olegz.vf.report.dao.mapper.ReportsMapper;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportGroup;
import dev.olegz.vf.report.domain.ReportGroupOrganization;
import dev.olegz.vf.report.domain.ReportGroupSchedule;

@Repository("reportsDao")
public class ReportsDaoImpl implements ReportsDao {
    private final ReportsMapper mapper;

    public ReportsDaoImpl(ReportsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insertReportGroupReport(int reportId, int reportGroupId) {
        mapper.insertReportGroupReport(reportId, reportGroupId, Timestamp.from(Instant.now()));
    }

    @Override
    public void insertReportGroupSchedule(ReportGroupSchedule schedule) {
        mapper.insertReportSchedule(schedule);
    }

    @Override
    public void deleteReportGroupSchedule(int reportId, int reportGroupId) {
        mapper.deleteReportSchedule(reportId, reportGroupId);
    }

    @Override
    public void deleteReportExecutions(int reportId) {
        mapper.deleteReportExecutions(reportId);
    }

    @Override
    public void insertReportExecution(ReportExecution reportExecution) {
        reportExecution.errorMessage = StringUtils.truncate(reportExecution.errorMessage, ReportExecution.MAX_ERROR_MSG_LEN);
        mapper.insertReportExecution(reportExecution);
    }

    @Override
    public void updateReportGroupScheduleExecutionDates(int scheduleId, Timestamp lastExecutionDate, Timestamp nextExecutionDate) {
        mapper.updateReportGroupScheduleExecutionDates(scheduleId, lastExecutionDate, nextExecutionDate);
    }

    @Override
    public Integer getReportGroupId(int reportId, Integer organizationId, Integer reportGroupId) {
        return mapper.selectReportGroupId(reportId, organizationId, reportGroupId);
    }

    @Override
    public Report getReportForExecution(int reportId) {
        return mapper.selectReportForExecution(reportId);
    }

    @Override
    public List<Report> getReports(Integer reportId, Integer reportGroupId, Boolean analytic, Integer organizationId) {
        Byte reportType = analytic == null ? null : analytic ? Report.TYPE_ANALYTICS : Report.TYPE_SUMMARY;
        return organizationId == null ?
            mapper.selectSystemReports(reportId, reportGroupId, reportType) :
            mapper.selectOrganizationReports(organizationId, reportId, reportGroupId, reportType);
    }

    @Override
    public Report getReportWithFields(int reportId, int reportGroupId) {
        return mapper.selectReportWithFields(reportId, reportGroupId);
    }

    @Override
    public List<ReportGroupSchedule> getReportSchedules() {
        return mapper.selectPastReportSchedules(Timestamp.from(Instant.now()));
    }

    @Override
    public List<ReportGroupSchedule> getReportSchedules(int reportId) {
        return mapper.selectReportSchedules(reportId);
    }

    @Override
    public ReportGroupSchedule getReportScheduleForUpdate(int scheduleId) {
        return mapper.selectReportScheduleForUpdate(scheduleId);
    }

    @Override
    @Transactional
    public void insertReportGroupOrganization(int reportGroupId, int organizationId) {
        if (mapper.insertReportGroupOrganization(reportGroupId, organizationId, Timestamp.from(Instant.now()))) {
            // delete the group from all children
            mapper.deleteReportGroupOrganizations(reportGroupId, organizationId, true);
        } else {
            throw new ObjectNotFoundException("No such report group for organizations");
        }
    }

    @Override
    public boolean deleteReportGroupOrganization(int reportGroupId, int organizationId) {
        return mapper.deleteReportGroupOrganizations(reportGroupId, organizationId, false);
    }

    @Override
    public void deleteReportGroupOrganizationsByOrganization(int organizationId) {
        mapper.deleteReportGroupOrganizationsByOrganization(organizationId);
    }

    @Override
    public ReportGroupOrganization getReportGroupOrganization(int reportGroupId, int organizationId) {
        return mapper.selectReportGroupOrganization(reportGroupId, organizationId);
    }

    @Override
    public List<ReportGroupOrganization> getReportGroupOrganizations() {
        return mapper.selectReportGroupOrganizations();
    }

    @Override
    public List<ReportGroup> getOrganizationReportGroups(int organizationId, boolean analytic, boolean all) {
        return mapper.selectOrganizationReportGroups(organizationId, analytic, all);
    }

    @Override
    public List<ReportExecution> getScheduledReportExecutions(int reportId, int reportGroupId, boolean analytic, Integer organizationId, Timestamp startDate, Timestamp endDate) {
        return mapper.selectScheduledReportExecutions(reportId, reportGroupId, analytic, organizationId, startDate, endDate);
    }

    @Override
    public ReportExecution getReportExecution(String objectId) {
        return mapper.selectReportExecutionByObjectId(objectId);
    }

    @Override
    public void deleteOnDemandReportExecutions(Timestamp expirationDate) {
        mapper.deleteOnDemandReportExecutions(expirationDate);
    }

    @Override
    public List<ReportData> executeQuery(String query, Object[] params) {
        Object[] values = (params == null) || (params.length == 0) ? new Object[10] :
            params.length < 10 ? Arrays.copyOf(params, 10) : params;
        List<ReportData> data = mapper.executeQuery(query,
            values[0], values[1], values[2], values[3], values[4],
            values[5], values[6], values[7], values[8], values[9]);

        data.removeIf(Objects::isNull);
        return data.isEmpty() ? null : data;
    }

}
