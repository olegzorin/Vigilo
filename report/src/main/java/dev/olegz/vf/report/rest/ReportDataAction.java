package dev.olegz.vf.report.rest;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.objectmap.StringMapper;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.service.ReportsService;
import dev.olegz.vf.report.storage.ReportOutputStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.dataformat.csv.CsvMapper;
import tools.jackson.dataformat.csv.CsvSchema;

@Component
public class ReportDataAction {
    public static final byte RESPONSE_FORMAT_ZIP = 0;
    public static final byte RESPONSE_FORMAT_JSON = 1;
    public static final String CONTENT_TYPE_ZIP = "application/zip";

    private static final Logger logger = LoggerFactory.getLogger(ReportDataAction.class);

    private final ReportsService reportsService;
    private final JwtService jwtService;
    private final ReportOutputStore outputStore;

    public ReportDataAction(ReportsService reportsService, JwtService jwtService, ReportOutputStore outputStore) {
        this.reportsService = reportsService;
        this.jwtService = jwtService;
        this.outputStore = outputStore;
    }

    public Download getExecutionData(String token, Byte responseFormat) {
        ReportClaims claims;
        try {
            claims = jwtService.verifyJwt(token, ReportClaims.class);
        } catch (Exception e) {
            return Download.status(HttpStatus.UNAUTHORIZED);
        }

        ReportExecution execution;
        try {
            execution = reportsService.getReportExecution(claims.oid);
        } catch (ObjectNotFoundException e) {
            return Download.status(HttpStatus.NOT_FOUND);
        }

        if (execution.errorMessage != null) {
            return new Download(HttpStatus.MULTI_STATUS,
                execution.errorMessage.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN, null);
        }

        byte[] data = execution.blobOutput;
        if (data == null) {
            try {
                data = outputStore.get(claims.oid);
            } catch (Exception e) {
                logger.error("Exception in getting report data from secondary storage {}", claims, e);
            }
        }
        if ((data == null) || (data.length == 0)) return Download.status(HttpStatus.NO_CONTENT);

        if ((responseFormat != null) && (responseFormat == RESPONSE_FORMAT_JSON)) {
            byte[] json = zipCsvToJson(data);
            return ((json == null) || (json.length == 0)) ? Download.status(HttpStatus.NO_CONTENT) :
                new Download(HttpStatus.OK, json, MediaType.APPLICATION_JSON, null);
        }

        String reportName = execution.reportName == null ? "report" : execution.reportName;
        String fileName = reportName.replaceAll("[^A-Za-z0-9._-]", "_") + ".zip";
        return new Download(HttpStatus.OK, data, MediaType.parseMediaType(CONTENT_TYPE_ZIP), fileName);
    }

    private static byte[] zipCsvToJson(byte[] zipData) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry = zip.getNextEntry();
            if (entry == null) return null;
            byte[] csv = zip.readAllBytes();
            CsvSchema schema = CsvSchema.emptySchema().withHeader();
            try (var rows = new CsvMapper().readerFor(Map.class).with(schema).readValues(csv)) {
                return StringMapper.toBytes(rows.readAll());
            }
        } catch (Exception e) {
            logger.error("Exception in converting report ZIP to JSON", e);
            return null;
        }
    }

    public record Download(HttpStatus status, byte[] data, MediaType contentType, String fileName) {
        static Download status(HttpStatus status) {
            return new Download(status, null, null, null);
        }
    }
}
