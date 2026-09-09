package dev.olegz.vf.report.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import dev.olegz.vf.report.ReportExecutor;
import dev.olegz.vf.report.dao.ReportsDao;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportGroupOrganization;
import dev.olegz.vf.report.domain.ReportGroupSchedule;
import dev.olegz.vf.report.storage.ReportOutputStore;

class ReportsServiceImplTest {
    @Test
    void setReportGroupOrganizationCreatesMissingAssignment() {
        AtomicReference<Object[]> inserted = new AtomicReference<>();
        ReportsDao dao = reportGroupOrganizationDao(null, inserted);
        ReportsService service = new ReportsServiceImpl(dao, new ReportExecutor(dao));

        service.setReportGroupOrganization(7, 12);

        assertEquals(7, inserted.get()[0]);
        assertEquals(12, inserted.get()[1]);
    }

    @Test
    void setReportGroupOrganizationDoesNothingForInheritedAssignment() {
        ReportGroupOrganization inherited = new ReportGroupOrganization();
        inherited.organizationId = 5;
        ReportsDao dao = reportGroupOrganizationDao(inherited, new AtomicReference<>());
        ReportsService service = new ReportsServiceImpl(dao, new ReportExecutor(dao));

        service.setReportGroupOrganization(7, 12);
    }

    @Test
    void executeOnDemandStoresZipInDatabase() throws Exception {
        AtomicReference<ReportExecution> inserted = new AtomicReference<>();
        Report report = report();
        ReportsDao dao = dao(report, inserted);
        ReportsService service = new ReportsServiceImpl(dao, new ReportExecutor(dao));

        ReportExecution execution = service.executeOnDemand(7, 12, null, ZoneOffset.UTC);

        assertEquals(1, execution.rowCount);
        assertNotNull(execution.objectId);
        assertNotNull(execution.blobOutput);
        assertNull(execution.errorMessage);
        assertEquals(execution, inserted.get());
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(execution.blobOutput))) {
            assertEquals("daily.csv", zip.getNextEntry().getName());
            assertArrayEquals("Value\nhello\n".getBytes(StandardCharsets.UTF_8), zip.readAllBytes());
        }
    }

    @Test
    void runScheduledReportsExecutesDueScheduleAndAdvancesIt() throws Exception {
        Instant now = Instant.parse("2026-08-20T08:00:00Z");
        Report report = report();
        ReportGroupSchedule schedule = schedule(report);
        List<ReportExecution> inserted = new ArrayList<>();
        AtomicReference<Timestamp[]> executionDates = new AtomicReference<>();
        RecordingTransactions transactions = new RecordingTransactions();
        ReportsDao dao = scheduledDao(report, schedule, schedule, inserted, executionDates, transactions);
        ReportsService service = new ReportsServiceImpl(
            dao, new ReportExecutor(dao), Clock.fixed(now, ZoneOffset.UTC), transactions);

        int count = service.runScheduledReports();

        assertEquals(1, count);
        assertEquals(1, inserted.size());
        ReportExecution execution = inserted.getFirst();
        assertEquals(31, execution.scheduleId);
        assertEquals(Timestamp.from(now), execution.scheduleDate);
        assertEquals(Timestamp.from(now), execution.executionDate);
        assertEquals(1, execution.rowCount);
        assertNotNull(execution.objectId);
        assertNotNull(execution.blobOutput);
        assertNull(execution.errorMessage);
        assertArrayEquals(new Timestamp[] {
            Timestamp.from(now), Timestamp.from(Instant.parse("2026-08-21T08:00:00Z"))
        }, executionDates.get());
        assertEquals(1, transactions.executions);
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(execution.blobOutput))) {
            assertEquals("daily.csv", zip.getNextEntry().getName());
            assertArrayEquals("Value\nhello\n".getBytes(StandardCharsets.UTF_8), zip.readAllBytes());
        }
    }

    @Test
    void runScheduledReportsDiscardsPreparedExecutionWhenScheduleChanges() {
        Instant now = Instant.parse("2026-08-20T08:00:00Z");
        Report report = report();
        ReportGroupSchedule schedule = schedule(report);
        ReportGroupSchedule changed = schedule(report);
        changed.nextExecutionDate = Timestamp.from(Instant.parse("2026-08-21T08:00:00Z"));
        List<ReportExecution> inserted = new ArrayList<>();
        AtomicReference<Timestamp[]> executionDates = new AtomicReference<>();
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingOutputStore outputStore = new RecordingOutputStore();
        ReportsDao dao = scheduledDao(
            report, schedule, changed, inserted, executionDates, transactions, largeValue());
        ReportsService service = new ReportsServiceImpl(
            dao, new ReportExecutor(dao), Clock.fixed(now, ZoneOffset.UTC), transactions, outputStore);

        int count = service.runScheduledReports();

        assertEquals(0, count);
        assertTrue(inserted.isEmpty());
        assertNull(executionDates.get());
        assertNull(outputStore.data);
        assertEquals(1, transactions.executions);
    }

    @Test
    void largeOnDemandAndScheduledOutputsAreStoredExternally() {
        String largeValue = largeValue();
        Report report = report();
        RecordingOutputStore onDemandStore = new RecordingOutputStore();
        AtomicReference<ReportExecution> onDemandInsert = new AtomicReference<>();
        ReportsDao onDemandDao = dao(report, onDemandInsert, largeValue);
        ReportsService onDemandService = new ReportsServiceImpl(
            onDemandDao, new ReportExecutor(onDemandDao), Clock.systemUTC(),
            new RecordingTransactions(), onDemandStore);

        ReportExecution onDemand = onDemandService.executeOnDemand(7, 12, null, ZoneOffset.UTC);

        assertExternalOutput(onDemand, onDemandStore);
        assertEquals(onDemand, onDemandInsert.get());

        Instant now = Instant.parse("2026-08-20T08:00:00Z");
        ReportGroupSchedule schedule = schedule(report);
        List<ReportExecution> scheduledInserts = new ArrayList<>();
        RecordingTransactions transactions = new RecordingTransactions();
        RecordingOutputStore scheduledStore = new RecordingOutputStore();
        ReportsDao scheduledDao = scheduledDao(report, schedule, schedule, scheduledInserts,
            new AtomicReference<>(), transactions, largeValue);
        ReportsService scheduledService = new ReportsServiceImpl(
            scheduledDao, new ReportExecutor(scheduledDao), Clock.fixed(now, ZoneOffset.UTC),
            transactions, scheduledStore);

        assertEquals(1, scheduledService.runScheduledReports());
        assertEquals(1, scheduledInserts.size());
        assertExternalOutput(scheduledInserts.getFirst(), scheduledStore);
    }

    private static void assertExternalOutput(ReportExecution execution, RecordingOutputStore store) {
        assertNotNull(execution.objectId);
        assertNull(execution.blobOutput);
        assertNull(execution.errorMessage);
        assertEquals(execution.objectId, store.objectId);
        assertNotNull(store.data);
        assertTrue(store.data.length > 64_000);
    }

    private static Report report() {
        Report report = new Report();
        report.reportId = 7;
        report.reportName = "daily";
        report.sqlQuery = "SELECT value";
        ReportField field = new ReportField();
        field.name = "value";
        field.columnName = "s1";
        field.displayName = "Value";
        report.fields = List.of(field);
        return report;
    }

    private static ReportGroupSchedule schedule(Report report) {
        ReportGroupSchedule schedule = new ReportGroupSchedule();
        schedule.scheduleId = 31;
        schedule.reportId = report.reportId;
        schedule.reportGroupId = 1;
        schedule.schedule = "0 0 8 * * ?";
        schedule.timezone = "UTC";
        schedule.parameters = Map.of();
        return schedule;
    }

    private static ReportsDao dao(Report report, AtomicReference<ReportExecution> inserted) {
        return dao(report, inserted, "hello");
    }

    private static ReportsDao dao(Report report, AtomicReference<ReportExecution> inserted, String value) {
        return (ReportsDao) Proxy.newProxyInstance(
            ReportsDao.class.getClassLoader(), new Class<?>[] {ReportsDao.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getReportGroupId" -> 2;
                case "getReportForExecution" -> report;
                case "executeQuery" -> {
                    ReportData row = new ReportData();
                    row.s1 = value;
                    yield List.of(row);
                }
                case "insertReportExecution" -> {
                    inserted.set((ReportExecution) args[0]);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportsDao scheduledDao(Report report, ReportGroupSchedule schedule,
        ReportGroupSchedule lockedSchedule,
        List<ReportExecution> inserted, AtomicReference<Timestamp[]> executionDates,
        RecordingTransactions transactions)
    {
        return scheduledDao(report, schedule, lockedSchedule, inserted, executionDates, transactions, "hello");
    }

    private static ReportsDao scheduledDao(Report report, ReportGroupSchedule schedule,
        ReportGroupSchedule lockedSchedule,
        List<ReportExecution> inserted, AtomicReference<Timestamp[]> executionDates,
        RecordingTransactions transactions, String value)
    {
        return (ReportsDao) Proxy.newProxyInstance(
            ReportsDao.class.getClassLoader(), new Class<?>[] {ReportsDao.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getReportSchedules" -> List.of(schedule);
                case "getReportGroupOrganizations" -> List.of();
                case "getReportScheduleForUpdate" -> {
                    assertTrue(transactions.active.get());
                    yield lockedSchedule;
                }
                case "getReportForExecution" -> report;
                case "executeQuery" -> {
                    assertFalse(transactions.active.get());
                    ReportData row = new ReportData();
                    row.s1 = value;
                    yield List.of(row);
                }
                case "insertReportExecution" -> {
                    assertTrue(transactions.active.get());
                    inserted.add((ReportExecution) args[0]);
                    yield null;
                }
                case "updateReportGroupScheduleExecutionDates" -> {
                    assertTrue(transactions.active.get());
                    executionDates.set(new Timestamp[] {(Timestamp) args[1], (Timestamp) args[2]});
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static ReportsDao reportGroupOrganizationDao(ReportGroupOrganization existing,
        AtomicReference<Object[]> inserted)
    {
        return (ReportsDao) Proxy.newProxyInstance(
            ReportsDao.class.getClassLoader(), new Class<?>[] {ReportsDao.class}, (proxy, method, args) -> switch (method.getName()) {
                case "getReportGroupOrganization" -> existing;
                case "insertReportGroupOrganization" -> {
                    inserted.set(args);
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static String largeValue() {
        byte[] data = new byte[100_000];
        new Random(1).nextBytes(data);
        return Base64.getEncoder().encodeToString(data);
    }

    private static final class RecordingOutputStore implements ReportOutputStore {
        private String objectId;
        private byte[] data;

        @Override
        public byte[] get(String objectId) {
            return data;
        }

        @Override
        public void put(String objectId, byte[] data) {
            this.objectId = objectId;
            this.data = data;
        }
    }

    private static final class RecordingTransactions implements TransactionOperations {
        private final AtomicBoolean active = new AtomicBoolean();
        private int executions;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            assertTrue(active.compareAndSet(false, true));
            executions++;
            try {
                return action.doInTransaction(null);
            } finally {
                active.set(false);
            }
        }
    }
}
