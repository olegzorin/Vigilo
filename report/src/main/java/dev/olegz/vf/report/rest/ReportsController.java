package dev.olegz.vf.report.rest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController("reportsController")
@RequestMapping(path = "/vf/reports")
public class ReportsController {
    private static final String API_KEY = "API_KEY";
    private final ReportsAction reportsAction;
    private final ReportActionContextFactory contextFactory;
    private final ReportDataAction reportDataAction;

    public ReportsController(ReportsAction reportsAction, ReportActionContextFactory contextFactory,
        ReportDataAction reportDataAction)
    {
        this.reportsAction = reportsAction;
        this.contextFactory = contextFactory;
        this.reportDataAction = reportDataAction;
    }

    @GetMapping(path = "/groups/{organizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportActionResponse getReportGroups(@RequestHeader(API_KEY) String key,
        @PathVariable int organizationId, @RequestParam(required = false) Boolean analytic,
        @RequestParam(required = false) Boolean all)
    {
        return reportsAction.getReportGroups(contextFactory.current(key), organizationId, analytic, all);
    }

    @PutMapping(path = "/groups/{organizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportActionResponse putReportGroupOrganization(@RequestHeader(API_KEY) String key,
        @PathVariable int organizationId, @RequestParam int reportGroupId)
    {
        return reportsAction.putReportGroupOrganization(
            contextFactory.current(key), organizationId, reportGroupId);
    }

    @DeleteMapping(path = "/groups/{organizationId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportActionResponse deleteReportGroupOrganization(@RequestHeader(API_KEY) String key,
        @PathVariable int organizationId, @RequestParam int reportGroupId)
    {
        return reportsAction.deleteReportGroupOrganization(contextFactory.current(key), organizationId, reportGroupId);
    }

    @GetMapping(path = "/reports", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportActionResponse getReports(@RequestHeader(API_KEY) String key,
        @RequestParam(required = false) Integer reportId,
        @RequestParam(required = false) Integer organizationId,
        @RequestParam(required = false) Integer reportGroupId,
        @RequestParam(required = false) Boolean analytic)
    {
        return reportsAction.getReports(
            contextFactory.current(key), reportId, organizationId, reportGroupId, analytic);
    }

    @GetMapping(path = "/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportActionResponse getReportExecutions(@RequestHeader(API_KEY) String key,
        @RequestParam Instant startDate, @RequestParam Instant endDate,
        @RequestParam int reportId, @RequestParam int reportGroupId,
        @RequestParam(required = false) Integer organizationId)
    {
        return reportsAction.getReportExecutions(
            contextFactory.current(key), startDate, endDate, reportId, reportGroupId, organizationId,
            downloadBaseUrl());
    }

    @GetMapping(path = "/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ReportActionResponse generateReport(@RequestHeader(API_KEY) String key,
        @RequestParam int reportId, @RequestParam(required = false) Integer organizationId,
        @RequestParam Map<String, String> queryParameters)
    {
        Map<String, String> parameters = new HashMap<>(queryParameters);
        parameters.remove("reportId");
        parameters.remove("organizationId");
        return reportsAction.runReport(
            contextFactory.current(key), reportId, organizationId, parameters, downloadBaseUrl());
    }

    @GetMapping(path = "/data/{token}")
    public ResponseEntity<byte[]> getReportData(@PathVariable String token,
        @RequestParam(required = false) Byte responseFormat)
    {
        ReportDataAction.Download download = reportDataAction.getExecutionData(token, responseFormat);
        ResponseEntity.BodyBuilder response = ResponseEntity.status(download.status());
        if (download.contentType() != null) response.contentType(download.contentType());
        if (download.fileName() != null) {
            response.header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + download.fileName() + "\"");
        }
        return response.body(download.data());
    }

    private static String downloadBaseUrl() {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
            .path("/vf/reports/data/")
            .build()
            .toUriString();
    }
}
