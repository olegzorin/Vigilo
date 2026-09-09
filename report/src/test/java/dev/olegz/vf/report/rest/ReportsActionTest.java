package dev.olegz.vf.report.rest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.dao.OrganizationDao;
import dev.olegz.vf.registry.domain.account.Organization;
import dev.olegz.vf.registry.domain.account.User;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportDataType;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.service.ReportsService;
import tools.jackson.databind.JsonNode;

class ReportsActionTest {
    @Test
    void getReportsReturnsApiModelWithoutSql() {
        Report report = new Report();
        report.reportId = 7;
        report.reportName = "daily";
        report.sqlQuery = "secret SQL";
        ReportsAction action = action(serviceReturning(List.of(report)));

        ReportsAction.Response response = action.getReports(context(12), 7, 12, null, null);

        assertEquals("daily", response.reports.getFirst().name);
        JsonNode json = StringMapper.valueToTree(response.reports.getFirst());
        assertFalse(json.has("sqlQuery"));
    }

    @Test
    void getReportsRejectsAnotherOrganization() {
        ReportsAction action = action(serviceReturning(List.of()));
        assertThrows(AccessDeniedException.class,
            () -> action.getReports(context(12), null, 13, null, null));
    }

    @Test
    void putReportGroupOrganizationAllowsAncestorAdministrator() {
        AtomicReference<Object[]> assignment = new AtomicReference<>();
        ReportsAction action = action(assignmentService(assignment));

        action.putReportGroupOrganization(context(10, 3, Map.of(
            10, organization(10, null, 3),
            20, organization(20, 10, 4),
            30, organization(30, 20, 5))), 30, 7);

        assertEquals(7, assignment.get()[0]);
        assertEquals(30, assignment.get()[1]);
    }

    @Test
    void putReportGroupOrganizationRejectsNonAdministrator() {
        ReportsAction action = action(assignmentService(new AtomicReference<>()));

        assertThrows(AccessDeniedException.class, () -> action.putReportGroupOrganization(
            context(10, 3, Map.of(
                10, organization(10, null, 4),
                20, organization(20, 10, 5))),
            20, 7));
    }

    @Test
    void scheduledSummaryReturnsSignedUrlsWithoutRawObjectId() {
        Report report = new Report();
        ReportExecution execution = new ReportExecution();
        execution.reportId = 7;
        execution.reportName = "daily";
        execution.objectId = "internal-id";
        execution.executionDate = Timestamp.from(Instant.parse("2026-08-20T08:00:00Z"));
        report.executions = List.of(execution);
        AtomicReference<ReportClaims> claims = new AtomicReference<>();
        ReportsAction action = new ReportsAction(scheduledService(report), jwtService(claims));

        ReportsAction.Response response = action.getReportExecutions(context(12),
            Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-21T00:00:00Z"),
            7, 3, 12, "https://api.example/vf/reports/data/");

        ApiReportExecution apiExecution = response.executions.getFirst();
        assertEquals("https://api.example/vf/reports/data/signed-token", apiExecution.url);
        assertEquals(apiExecution.url + "?responseFormat=1", apiExecution.urlJson);
        assertEquals("internal-id", claims.get().oid);
        assertTrue(claims.get().valid());
        assertNull(apiExecution.data);
        assertFalse(StringMapper.valueToTree(apiExecution).has("objectId"));
    }

    @Test
    void scheduledAnalyticReturnsInlineDataWithoutDownloadUrls() {
        Report report = new Report();
        report.reportId = 8;
        report.reportType = Report.TYPE_ANALYTICS;
        ReportField field = new ReportField();
        field.name = "value";
        field.dataType = ReportDataType.INTEGER.databaseValue();
        report.fields = List.of(field);
        ReportExecution execution = new ReportExecution();
        execution.reportId = report.reportId;
        execution.analyticOutput = "42";
        report.executions = List.of(execution);
        ReportsAction action = action(scheduledService(report));

        ReportsAction.Response response = action.getReportExecutions(context(12),
            Instant.parse("2026-08-20T00:00:00Z"), Instant.parse("2026-08-21T00:00:00Z"),
            8, 3, 12, "https://api.example/vf/reports/data/");

        ApiReportExecution apiExecution = response.executions.getFirst();
        assertNull(apiExecution.url);
        assertNull(apiExecution.urlJson);
        assertEquals(1, apiExecution.data.size());
        assertEquals(1, apiExecution.data.getFirst().id());
        assertEquals(42, apiExecution.data.getFirst().value());
    }

    private static ReportsAction action(ReportsService reportsService) {
        return new ReportsAction(reportsService, jwtService(new AtomicReference<>()));
    }

    private static JwtService jwtService(AtomicReference<ReportClaims> claims) {
        return (JwtService) Proxy.newProxyInstance(
            JwtService.class.getClassLoader(), new Class<?>[] {JwtService.class}, (proxy, method, args) -> {
                if ("createJwt".equals(method.getName())) {
                    claims.set((ReportClaims) args[0]);
                    return "signed-token";
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportsService scheduledService(Report report) {
        return (ReportsService) Proxy.newProxyInstance(
            ReportsService.class.getClassLoader(), new Class<?>[] {ReportsService.class}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "getScheduledReportExecutions" -> report;
                    case "getReportDictionary" -> List.of();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static ReportsService serviceReturning(List<Report> reports) {
        return (ReportsService) Proxy.newProxyInstance(
            ReportsService.class.getClassLoader(), new Class<?>[] {ReportsService.class}, (proxy, method, args) -> {
                if ("getReports".equals(method.getName())) return reports;
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportsService assignmentService(AtomicReference<Object[]> assignment) {
        return (ReportsService) Proxy.newProxyInstance(
            ReportsService.class.getClassLoader(), new Class<?>[] {ReportsService.class}, (proxy, method, args) -> {
                if ("setReportGroupOrganization".equals(method.getName())) {
                    assignment.set(args);
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportActionContext context(int organizationId) {
        return context(organizationId, 3, Map.of());
    }

    private static ReportActionContext context(int organizationId, int userId,
        Map<Integer, Organization> organizations)
    {
        User user = new User();
        user.userId = userId;
        user.organizationId = organizationId;
        OrganizationDao dao = (OrganizationDao) Proxy.newProxyInstance(
            OrganizationDao.class.getClassLoader(), new Class<?>[] {OrganizationDao.class},
            (proxy, method, args) -> "getOrganization".equals(method.getName()) ? organizations.get(args[0]) : null);
        return new ReportActionContext(user, dao);
    }

    private static Organization organization(int organizationId, Integer parentId, Integer adminUserId) {
        Organization organization = new Organization();
        organization.organizationId = organizationId;
        organization.parentId = parentId;
        organization.adminUserId = adminUserId;
        return organization;
    }
}
