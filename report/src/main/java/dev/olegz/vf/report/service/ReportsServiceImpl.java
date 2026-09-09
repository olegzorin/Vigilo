package dev.olegz.vf.report.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.common.exception.AccessDeniedException;
import dev.olegz.vf.common.exception.ObjectNotFoundException;
import dev.olegz.vf.common.util.CollectionOps;
import dev.olegz.vf.common.schedule.CronExpression;
import dev.olegz.vf.report.ReportExecutor;
import dev.olegz.vf.report.dao.ReportsDao;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportGroup;
import dev.olegz.vf.report.domain.ReportGroupOrganization;
import dev.olegz.vf.report.domain.ReportGroupSchedule;
import dev.olegz.vf.report.domain.ReportOutput;
import dev.olegz.vf.report.domain.ReportRequest;
import dev.olegz.vf.report.storage.ReportOutputStore;

@Service
public class ReportsServiceImpl implements ReportsService {
    private static final Logger logger = LoggerFactory.getLogger(ReportsServiceImpl.class);
    private static final int MAX_DB_OUTPUT = 64_000;
    private static final long ON_DEMAND_RETENTION_MILLIS = 86_400_000L;
    private static final long ONE_DAY_MILLIS = 86_400_000L;

    private final ReportsDao reportsDao;
    private final ReportExecutor reportExecutor;
    private final Clock clock;
    private final TransactionOperations transactions;
    private final ReportOutputStore outputStore;

    @Autowired
    public ReportsServiceImpl(ReportsDao reportsDao, ReportExecutor reportExecutor,
        PlatformTransactionManager transactionManager, ReportOutputStore outputStore)
    {
        this(reportsDao, reportExecutor, Clock.systemUTC(), new TransactionTemplate(transactionManager), outputStore);
    }

    ReportsServiceImpl(ReportsDao reportsDao, ReportExecutor reportExecutor) {
        this(reportsDao, reportExecutor, Clock.systemUTC(), directTransactions(), noExternalOutput());
    }

    ReportsServiceImpl(ReportsDao reportsDao, ReportExecutor reportExecutor, Clock clock) {
        this(reportsDao, reportExecutor, clock, directTransactions(), noExternalOutput());
    }

    ReportsServiceImpl(ReportsDao reportsDao, ReportExecutor reportExecutor, Clock clock,
        TransactionOperations transactions)
    {
        this(reportsDao, reportExecutor, clock, transactions, noExternalOutput());
    }

    ReportsServiceImpl(ReportsDao reportsDao, ReportExecutor reportExecutor, Clock clock,
        TransactionOperations transactions, ReportOutputStore outputStore)
    {
        this.reportsDao = reportsDao;
        this.reportExecutor = reportExecutor;
        this.clock = clock;
        this.transactions = transactions;
        this.outputStore = outputStore;
    }

    @Override
    public Report getReport(int reportId) {
        Report report = reportsDao.getReportForExecution(reportId);
        if (report == null) throw new ObjectNotFoundException("Report " + reportId + " not found");
        return report;
    }

    @Override
    public List<Report> getReports(Integer reportId, Integer organizationId, Integer reportGroupId, Boolean analytic) {
        return reportsDao.getReports(reportId, reportGroupId, analytic, organizationId);
    }

    @Override
    public List<ReportGroup> getOrganizationReportGroups(int organizationId, boolean analytic, boolean all) {
        return reportsDao.getOrganizationReportGroups(organizationId, analytic, all);
    }

    @Override
    public void setReportGroupOrganization(int reportGroupId, int organizationId) {
        ReportGroupOrganization assignment = reportsDao.getReportGroupOrganization(reportGroupId, organizationId);
        if (assignment != null) return;
        reportsDao.insertReportGroupOrganization(reportGroupId, organizationId);
    }

    @Override
    public void deleteReportGroupOrganization(int reportGroupId, int organizationId) {
        reportsDao.deleteReportGroupOrganization(reportGroupId, organizationId);
    }

    @Override
    public Integer getReportGroupId(int reportId, Integer organizationId) {
        return reportsDao.getReportGroupId(reportId, organizationId, null);
    }

    @Override
    public Report getScheduledReportExecutions(int reportId, int reportGroupId, Integer organizationId,
        Timestamp startDate, Timestamp endDate)
    {
        if (reportsDao.getReportGroupId(reportId, organizationId, reportGroupId) == null) {
            throw new AccessDeniedException("No access to report " + reportId);
        }
        Report report = reportsDao.getReportWithFields(reportId, reportGroupId);
        if (report == null) throw new ObjectNotFoundException("Scheduled report " + reportId + " not found");
        report.executions = reportsDao.getScheduledReportExecutions(
            reportId, reportGroupId, report.isAnalytic(), organizationId, startDate, endDate);
        return report;
    }

    @Override
    public List<Report.Dictionary> getReportDictionary(Report report) {
        if (StringUtils.isNotBlank(report.dictionarySql)) {
            List<ReportData> rows = reportsDao.executeQuery(report.dictionarySql, null);
            if (CollectionOps.findAny(report.fields, field -> ReportField.ID_FIELD_NAME.equals(field.name)) != null) {
                return CollectionOps.map(rows, row -> new Report.Dictionary(row.id, row.name, row.description));
            }
        }
        return CollectionOps.map(report.fields,
            field -> new Report.Dictionary(field.index, field.displayName, field.description));
    }

    @Override
    public ReportExecution executeOnDemand(int reportId, Integer organizationId, Map<String, String> parameters, ZoneId zoneId) {
        if (getReportGroupId(reportId, organizationId) == null) {
            throw new AccessDeniedException("No access to report " + reportId);
        }

        Report report = getReport(reportId);
        ReportRequest request = new ReportRequest();
        request.requestType = ReportRequest.TYPE_DEMAND;
        request.reportId = reportId;
        request.organizationId = organizationId;
        request.objectId = request.makeObjectId();
        request.params = report.getParamValues(parameters, 0, organizationId, zoneId);

        ReportExecution execution = new ReportExecution(request);
        execution.reportName = report.reportName;
        long started = System.currentTimeMillis();
        try {
            execution.rowCount = reportExecutor.execute(report, request.params);
            storeSummaryOutput(execution,
                zip(report.reportName + ".csv", new ReportOutput(report).exportCsvWithHeaders()));
        } catch (Exception e) {
            logger.error("Report {} execution failed", reportId, e);
            execution.blobOutput = null;
            execution.errorMessage = StringUtils.truncate("Internal error: " + e.getMessage(), ReportExecution.MAX_ERROR_MSG_LEN);
        }
        execution.executionDate = Timestamp.from(Instant.now());
        execution.executionTime = Math.toIntExact(Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - started));
        reportsDao.insertReportExecution(execution);
        return execution;
    }

    @Override
    public int runScheduledReports() {
        List<ReportGroupSchedule> schedules = reportsDao.getReportSchedules();
        if ((schedules == null) || schedules.isEmpty()) return 0;

        Map<Integer, List<ReportGroupOrganization>> organizationsByGroup = new HashMap<>();
        List<ReportGroupOrganization> organizations = reportsDao.getReportGroupOrganizations();
        if (organizations != null) {
            for (ReportGroupOrganization organization : organizations) {
                organizationsByGroup.computeIfAbsent(organization.reportGroupId, _ -> new ArrayList<>())
                    .add(organization);
            }
        }

        int executionCount = 0;
        for (ReportGroupSchedule schedule : schedules) {
            try {
                ScheduledReportBatch batch = prepareScheduledReport(
                    schedule, organizationsByGroup.get(schedule.reportGroupId));
                Boolean committed = transactions.execute(_ -> finalizeScheduledReport(batch));
                if (Boolean.TRUE.equals(committed)) executionCount += batch.executions.size();
            } catch (Exception e) {
                logger.error("Scheduled report processing failed: {}", schedule, e);
            }
        }
        return executionCount;
    }

    private ScheduledReportBatch prepareScheduledReport(ReportGroupSchedule schedule,
        List<ReportGroupOrganization> organizations) throws ParseException
    {
        long now = clock.millis();
        long scheduleTime = schedule.nextExecutionDate == null ? now : schedule.nextExecutionDate.getTime();
        long lastTime = schedule.lastExecutionDate == null ? now - ONE_DAY_MILLIS : schedule.lastExecutionDate.getTime();
        ZoneId scheduleZone = zoneId(schedule.timezone);
        CronExpression cron = new CronExpression(schedule.schedule);
        Report parameterReport = getReport(schedule.reportId);
        List<ReportExecution> executions = new ArrayList<>();
        Map<ReportExecution, byte[]> externalOutputs = new IdentityHashMap<>();
        long nextExecutionTime;

        if (schedule.organizationalType == 0) {
            Object[] params = parameterReport.getParamValues(schedule.parameters, lastTime, 0, scheduleZone);
            executions.add(executeScheduled(schedule, null, scheduleTime, params, externalOutputs));
            nextExecutionTime = nextFire(cron, scheduleZone, now);
        } else {
            int organizationParamIndex = parameterReport.getOrgParamIndex();
            if (organizationParamIndex < 0) {
                throw new IllegalArgumentException("Missing organizationId parameter for report " + schedule.reportId);
            }

            nextExecutionTime = Long.MAX_VALUE;
            if (organizations != null) {
                for (ReportGroupOrganization organization : organizations) {
                    ZoneId organizationZone = zoneId(organization.timezone == null ? schedule.timezone : organization.timezone);
                    long organizationFireTime = nextFire(cron, organizationZone,
                        schedule.lastExecutionDate == null ? lastTime : schedule.lastExecutionDate.getTime());
                    if (organizationFireTime <= now) {
                        Object[] params = parameterReport.getParamValues(
                            schedule.parameters, lastTime, organization.organizationId, organizationZone);
                        params[organizationParamIndex] = organization.organizationId;
                        executions.add(executeScheduled(
                            schedule, organization.organizationId, scheduleTime, params, externalOutputs));
                        organizationFireTime = nextFire(cron, organizationZone, now);
                    }
                    nextExecutionTime = Math.min(nextExecutionTime, organizationFireTime);
                }
            }
            if (nextExecutionTime == Long.MAX_VALUE) {
                nextExecutionTime = nextFire(cron, scheduleZone, now);
            }
        }

        return new ScheduledReportBatch(
            schedule, executions, externalOutputs, new Timestamp(scheduleTime), new Timestamp(nextExecutionTime));
    }

    private boolean finalizeScheduledReport(ScheduledReportBatch batch) {
        ReportGroupSchedule locked = reportsDao.getReportScheduleForUpdate(batch.schedule.scheduleId);
        if (!sameScheduleOccurrence(batch.schedule, locked)) {
            logger.info("Scheduled report changed while it was running; discard prepared execution: {}", batch.schedule);
            return false;
        }

        for (ReportExecution execution : batch.executions) {
            byte[] externalOutput = batch.externalOutputs.get(execution);
            if (externalOutput != null) {
                try {
                    outputStore.put(execution.objectId, externalOutput);
                } catch (Exception e) {
                    logger.error("Scheduled report {} output upload failed for organization {}",
                        execution.reportId, execution.organizationId, e);
                    execution.errorMessage = StringUtils.truncate(
                        "Internal error: " + e.getMessage(), ReportExecution.MAX_ERROR_MSG_LEN);
                }
            }
            reportsDao.insertReportExecution(execution);
        }
        reportsDao.updateReportGroupScheduleExecutionDates(
            batch.schedule.scheduleId, batch.lastExecutionDate, batch.nextExecutionDate);
        return true;
    }

    private ReportExecution executeScheduled(ReportGroupSchedule schedule, Integer organizationId,
        long scheduleTime, Object[] params, Map<ReportExecution, byte[]> externalOutputs)
    {
        ReportRequest request = new ReportRequest();
        request.requestType = ReportRequest.TYPE_SCHEDULE;
        request.reportId = schedule.reportId;
        request.multiCloud = schedule.reportGroupId == ReportGroup.SYS_REPORT_GROUP_ID;
        request.scheduleId = schedule.scheduleId;
        request.scheduleDate = scheduleTime;
        request.organizationId = organizationId;
        request.params = params;
        request.objectId = request.makeObjectId();

        ReportExecution execution = new ReportExecution(request);
        long started = clock.millis();
        try {
            Report report = getReport(schedule.reportId);
            execution.reportName = report.reportName;
            execution.rowCount = reportExecutor.execute(report, params);
            ReportOutput output = new ReportOutput(report);
            if (report.isAnalytic()) {
                execution.analyticOutput = output.exportAnalyticCsv();
                execution.objectId = null;
            } else {
                execution.metadata = report.computeMetadata();
                byte[] data = zip(report.reportName + ".csv", output.exportCsvWithHeaders());
                if (data.length > MAX_DB_OUTPUT) externalOutputs.put(execution, data);
                else execution.blobOutput = data;
            }
        } catch (Exception e) {
            logger.error("Scheduled report {} execution failed for organization {}", schedule.reportId, organizationId, e);
            execution.blobOutput = null;
            execution.analyticOutput = null;
            execution.errorMessage = StringUtils.truncate("Internal error: " + e.getMessage(), ReportExecution.MAX_ERROR_MSG_LEN);
        }
        execution.executionDate = Timestamp.from(clock.instant());
        execution.executionTime = Math.toIntExact(Math.min(Integer.MAX_VALUE, clock.millis() - started));
        return execution;
    }

    private static boolean sameScheduleOccurrence(ReportGroupSchedule expected, ReportGroupSchedule actual) {
        return actual != null &&
            expected.reportId == actual.reportId &&
            expected.reportGroupId == actual.reportGroupId &&
            expected.organizationalType == actual.organizationalType &&
            Objects.equals(expected.parameters, actual.parameters) &&
            Objects.equals(expected.timezone, actual.timezone) &&
            Objects.equals(expected.schedule, actual.schedule) &&
            Objects.equals(expected.lastExecutionDate, actual.lastExecutionDate) &&
            Objects.equals(expected.nextExecutionDate, actual.nextExecutionDate);
    }

    private static ZoneId zoneId(String timezone) {
        return (timezone == null) || timezone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timezone);
    }

    private static long nextFire(CronExpression cron, ZoneId zoneId, long afterTime) {
        long next = cron.getTimeAfter(afterTime, TimeZone.getTimeZone(zoneId));
        if (next <= afterTime) throw new IllegalArgumentException("No next report execution date");
        return next;
    }

    private static TransactionOperations directTransactions() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(null);
            }
        };
    }

    private void storeSummaryOutput(ReportExecution execution, byte[] data) {
        if (data.length > MAX_DB_OUTPUT) {
            outputStore.put(execution.objectId, data);
            execution.blobOutput = null;
        } else {
            execution.blobOutput = data;
        }
    }

    private static ReportOutputStore noExternalOutput() {
        return new ReportOutputStore() {
            @Override
            public byte[] get(String objectId) {
                return null;
            }

            @Override
            public void put(String objectId, byte[] data) {
                throw new ApplicationFailureException("External report output is not configured");
            }
        };
    }

    private record ScheduledReportBatch(ReportGroupSchedule schedule, List<ReportExecution> executions,
        Map<ReportExecution, byte[]> externalOutputs, Timestamp lastExecutionDate, Timestamp nextExecutionDate) {}

    @Override
    public ReportExecution getReportExecution(String objectId) {
        ReportExecution execution = reportsDao.getReportExecution(objectId);
        if (execution == null) throw new ObjectNotFoundException("Report execution not found");
        return execution;
    }

    @Override
    public void deleteExpiredExecutions() {
        reportsDao.deleteOnDemandReportExecutions(new Timestamp(System.currentTimeMillis() - ON_DEMAND_RETENTION_MILLIS));
    }

    private static byte[] zip(String fileName, byte[] content) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(fileName));
            zip.write(content);
            zip.closeEntry();
            zip.finish();
            return output.toByteArray();
        } catch (Exception e) {
            throw new ApplicationFailureException("Cannot package report output", e);
        }
    }
}
