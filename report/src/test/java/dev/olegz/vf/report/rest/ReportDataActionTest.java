package dev.olegz.vf.report.rest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import dev.olegz.vf.common.exception.InvalidJwtException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.registry.service.encryption.JwtService;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.service.ReportsService;
import dev.olegz.vf.report.storage.ReportOutputStore;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class ReportDataActionTest {
    @Test
    void downloadsDatabaseZipAndConvertsItToJson() throws Exception {
        byte[] zip = zip("Name,Value\none,1\n");
        ReportExecution execution = execution(zip);
        ReportDataAction action = action(execution, store(null));

        ReportDataAction.Download zipped = action.getExecutionData("valid", null);
        ReportDataAction.Download json = action.getExecutionData("valid", ReportDataAction.RESPONSE_FORMAT_JSON);

        assertEquals(HttpStatus.OK, zipped.status());
        assertEquals(MediaType.parseMediaType(ReportDataAction.CONTENT_TYPE_ZIP), zipped.contentType());
        assertEquals("daily.zip", zipped.fileName());
        assertArrayEquals(zip, zipped.data());
        assertEquals(HttpStatus.OK, json.status());
        assertEquals(MediaType.APPLICATION_JSON, json.contentType());
        assertEquals("[{\"Name\":\"one\",\"Value\":\"1\"}]", new String(json.data(), StandardCharsets.UTF_8));
    }

    @Test
    void fallsBackToSecondaryStorageAndMapsTerminalStatuses() throws Exception {
        byte[] zip = zip("Name\none\n");
        ReportExecution withoutBlob = execution(null);
        ReportDataAction fallback = action(withoutBlob, store(zip));
        ReportDataAction unavailable = action(withoutBlob, store(null));
        ReportDataAction invalid = action(withoutBlob, store(null), true);
        ReportDataAction missing = new ReportDataAction(missingService(), jwtService(false), store(null));

        assertArrayEquals(zip, fallback.getExecutionData("valid", null).data());
        assertEquals(HttpStatus.NO_CONTENT, unavailable.getExecutionData("valid", null).status());
        assertEquals(HttpStatus.UNAUTHORIZED, invalid.getExecutionData("invalid", null).status());
        assertEquals(HttpStatus.NOT_FOUND, missing.getExecutionData("valid", null).status());
    }

    @Test
    void returnsExecutionErrorAsMultiStatus() {
        ReportExecution execution = execution(null);
        execution.errorMessage = "failed";

        ReportDataAction.Download result = action(execution, store(null)).getExecutionData("valid", null);

        assertEquals(HttpStatus.MULTI_STATUS, result.status());
        assertEquals(MediaType.TEXT_PLAIN, result.contentType());
        assertEquals("failed", new String(result.data(), StandardCharsets.UTF_8));
        assertNull(result.fileName());
    }

    private static ReportDataAction action(ReportExecution execution, ReportOutputStore store) {
        return action(execution, store, false);
    }

    private static ReportDataAction action(ReportExecution execution, ReportOutputStore store, boolean invalidJwt) {
        return new ReportDataAction(service(execution), jwtService(invalidJwt), store);
    }

    private static ReportOutputStore store(byte[] data) {
        return new ReportOutputStore() {
            @Override
            public byte[] get(String objectId) {
                return data;
            }

            @Override
            public void put(String objectId, byte[] output) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static JwtService jwtService(boolean invalid) {
        return (JwtService) Proxy.newProxyInstance(
            JwtService.class.getClassLoader(), new Class<?>[] {JwtService.class}, (proxy, method, args) -> {
                if (!"verifyJwt".equals(method.getName())) throw new UnsupportedOperationException(method.getName());
                if (invalid) throw new InvalidJwtException();
                ReportClaims claims = new ReportClaims();
                claims.ty = ReportClaims.TYPE_REPORT;
                claims.exp = Long.MAX_VALUE;
                claims.oid = "internal-id";
                return claims;
            });
    }

    private static ReportsService service(ReportExecution execution) {
        return (ReportsService) Proxy.newProxyInstance(
            ReportsService.class.getClassLoader(), new Class<?>[] {ReportsService.class}, (proxy, method, args) -> {
                if ("getReportExecution".equals(method.getName())) return execution;
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportsService missingService() {
        return (ReportsService) Proxy.newProxyInstance(
            ReportsService.class.getClassLoader(), new Class<?>[] {ReportsService.class}, (proxy, method, args) -> {
                if ("getReportExecution".equals(method.getName())) throw new ObjectNotFoundException("missing");
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportExecution execution(byte[] blobOutput) {
        ReportExecution execution = new ReportExecution();
        execution.reportId = 7;
        execution.reportName = "daily";
        execution.blobOutput = blobOutput;
        return execution;
    }

    private static byte[] zip(String csv) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("daily.csv"));
            zip.write(csv.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }
}
