package dev.olegz.vf.report.dao;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import javax.sql.DataSource;

import dev.olegz.vf.common.VigiloEnvironment;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.report.dao.mapper.ReportsMapper;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportExecution;
import dev.olegz.vf.report.domain.ReportGroupSchedule;
import dev.olegz.vf.report.domain.ReportParam;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import static org.junit.jupiter.api.Assertions.*;

class PostgresqlNativeReportTypesDaoTest {
    private static final int REPORT_ID = 2_000_000_001;
    private static final int GROUP_ID = 30_001;

    @Configuration
    @Import(DataSourceConfig.class)
    @MapperScan(
        basePackages = "dev.olegz.vf.report.dao.mapper",
        sqlSessionTemplateRef = "sqlSessionTemplate")
    static class TestConfiguration {
        static {
            PropertyStore.start();
        }
    }

    @BeforeAll
    static void oneTimeSetUp() {
        VigiloEnvironment.getHomeDir();
    }

    private AnnotationConfigApplicationContext context;
    private PlatformTransactionManager transactionManager;
    private TransactionStatus transaction;
    private ReportsMapper mapper;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        transactionManager = context.getBean(PlatformTransactionManager.class);
        transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        mapper = context.getBean(ReportsMapper.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
    }

    @AfterEach
    void tearDown() {
        transactionManager.rollback(transaction);
        context.close();
    }

    @Test
    void booleansAndJsonMapsRoundTripThroughPostgresqlTypes() {
        Report report = new Report();
        report.reportId = REPORT_ID;
        report.reportName = "NativeReportTypes";
        report.reportType = 0;
        report.displayName = "Native report types";
        mapper.insertReportDefinition(report, "SELECT 1");

        ReportParam parameter = new ReportParam();
        parameter.reportId = REPORT_ID;
        parameter.name = "organizationId";
        parameter.index = 0;
        parameter.dataType = 0;
        parameter.required = true;
        parameter.displayName = "Organization";
        mapper.insertReportParam(parameter);

        jdbc.update("INSERT INTO report_groups (report_group_id, organizational_type, name) VALUES (?, 0, ?)",
            GROUP_ID, "Native report types");
        mapper.insertReportGroupReport(REPORT_ID, GROUP_ID, Timestamp.from(Instant.now()));

        ReportGroupSchedule schedule = new ReportGroupSchedule();
        schedule.reportId = REPORT_ID;
        schedule.reportGroupId = GROUP_ID;
        schedule.parameters = Map.of("organizationId", "1");
        schedule.timezone = "UTC";
        schedule.schedule = "0 0 1 * * ?";
        mapper.insertReportSchedule(schedule);

        ReportGroupSchedule storedSchedule = mapper.selectReportSchedules(REPORT_ID).getFirst();
        assertEquals(schedule.parameters, storedSchedule.parameters);

        ReportExecution execution = new ReportExecution();
        execution.reportId = REPORT_ID;
        execution.onDemand = true;
        execution.executionDate = Timestamp.from(Instant.now());
        execution.executionTime = 1;
        execution.rowCount = 1;
        execution.objectId = "native-report-types";
        execution.metadata = Map.of("rows", "1");
        mapper.insertReportExecution(execution);

        Boolean storedOnDemand = jdbc.queryForObject(
            "SELECT on_demand FROM report_execution_history WHERE object_id = ?",
            Boolean.class,
            execution.objectId);
        String storedRows = jdbc.queryForObject(
            "SELECT metadata ->> 'rows' FROM report_execution_history WHERE object_id = ?",
            String.class,
            execution.objectId);
        assertTrue(storedOnDemand);
        assertEquals("1", storedRows);
    }
}
