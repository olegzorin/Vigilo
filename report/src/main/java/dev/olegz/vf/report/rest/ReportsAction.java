package dev.olegz.vf.report.rest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.output.AnalyticReportOutput;
import dev.olegz.vf.report.service.ReportsService;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.registry.service.encryption.SigningAlgorithm;

@Component
public class ReportsAction {
    private final ReportsService reportsService;
    private final JwtService jwtService;

    public ReportsAction(ReportsService reportsService, JwtService jwtService) {
        this.reportsService = reportsService;
        this.jwtService = jwtService;
    }

    public Response getReportGroups(ReportActionContext context, int organizationId, Boolean analytic, Boolean all) {
        requireOrganizationAdmin(context, organizationId);
        Response response = new Response();
        response.groups = reportsService.getOrganizationReportGroups(
            organizationId, Boolean.TRUE.equals(analytic), all == null || all).stream().map(ApiReportGroup::new).toList();
        return response;
    }

    public Response putReportGroupOrganization(ReportActionContext context, int organizationId, int reportGroupId) {
        context.requireAdminOfOrganizationOrAncestor(organizationId);
        reportsService.setReportGroupOrganization(reportGroupId, organizationId);
        return new Response();
    }

    public Response deleteReportGroupOrganization(ReportActionContext context, int organizationId, int reportGroupId) {
        requireOrganizationAdmin(context, organizationId);
        reportsService.deleteReportGroupOrganization(reportGroupId, organizationId);
        return new Response();
    }

    public Response getReports(ReportActionContext context, Integer reportId, Integer organizationId,
        Integer reportGroupId, Boolean analytic)
    {
        context.user();
        if (organizationId != null) context.requireSameOrganization(organizationId);
        Response response = new Response();
        response.reports = reportsService.getReports(reportId, organizationId, reportGroupId, analytic)
            .stream().map(ApiReport::new).toList();
        return response;
    }

    public Response getReportExecutions(ReportActionContext context, Instant startDate, Instant endDate,
        int reportId, int reportGroupId, Integer organizationId, String downloadBaseUrl)
    {
        context.user();
        if (organizationId != null) context.requireSameOrganization(organizationId);
        if (!startDate.isBefore(endDate)) throw new ApplicationFailureException("startDate must be before endDate");
        Report report = reportsService.getScheduledReportExecutions(reportId, reportGroupId, organizationId,
            Timestamp.from(startDate), Timestamp.from(endDate));
        Response response = new Response();
        if (report.isAnalytic()) {
            AnalyticReportOutput.Reader reader = AnalyticReportOutput.getReader(report);
            response.executions = report.executions.stream()
                .map(execution -> new ApiReportExecution(
                    execution, null, reader.readReportData(execution.analyticOutput)))
                .toList();
        } else {
            response.executions = report.executions.stream()
                .map(execution -> new ApiReportExecution(execution, executionUrl(execution, downloadBaseUrl), null))
                .toList();
        }
        response.dictionary = reportsService.getReportDictionary(report);
        return response;
    }

    public Response runReport(ReportActionContext context, int reportId, Integer organizationId,
        Map<String, String> parameters, String downloadBaseUrl)
    {
        context.user();
        if (organizationId != null) context.requireSameOrganization(organizationId);
        ReportExecution execution = reportsService.executeOnDemand(
            reportId, organizationId, parameters, ZoneOffset.UTC);
        Response response = new Response();
        response.execution = new ApiReportExecution(execution, executionUrl(execution, downloadBaseUrl), null);
        return response;
    }

    private String executionUrl(ReportExecution execution, String downloadBaseUrl) {
        if ((execution.errorMessage != null) || (execution.objectId == null)) return null;
        String jwt = jwtService.createJwt(new ReportClaims(execution), SigningAlgorithm.HS512);
        return downloadBaseUrl + jwt;
    }

    private static void requireOrganizationAdmin(ReportActionContext context, int organizationId) {
        context.requireSameOrganization(organizationId);
        context.requireAdmin();
    }

    public static class Response extends ReportActionResponse {
        public List<ApiReportGroup> groups;
        public List<ApiReport> reports;
        public List<ApiReportExecution> executions;
        public List<Report.Dictionary> dictionary;
        public ApiReportExecution execution;
    }
}
