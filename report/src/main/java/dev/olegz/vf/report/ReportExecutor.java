package dev.olegz.vf.report;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.jexl3.JexlBuilder;
import org.apache.commons.jexl3.JexlEngine;
import org.apache.commons.jexl3.MapContext;
import org.apache.commons.jexl3.introspection.JexlPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import dev.olegz.vf.common.ApplicationFailureException;
import dev.olegz.vf.report.dao.ReportsDao;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportData;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportParam;

/** Executes the SQL/JEXL portion of a CareDaily report and populates its output fields. */
@Component
public class ReportExecutor {
    private static final Logger logger = LoggerFactory.getLogger(ReportExecutor.class);
    private static final JexlEngine JEXL = new JexlBuilder()
        .cache(100)
        .cacheThreshold(10_000)
        .strict(true)
        .silent(false)
        .debug(false)
        .permissions(JexlPermissions.parse("java.lang.*", "java.math.*", "java.util.*"))
        .create();

    @FunctionalInterface
    public interface QueryRunner {
        List<ReportData> execute(String query, Object[] params);
    }

    private final QueryRunner queryRunner;

    @Autowired
    public ReportExecutor(ReportsDao reportsDao) {
        this(reportsDao::executeQuery);
    }

    public ReportExecutor(QueryRunner queryRunner) {
        this.queryRunner = queryRunner;
    }

    public int execute(Report report, Object[] params) {
        if (report.sqlQuery == null) throw new ApplicationFailureException("missing SQL query");
        convertDateParameters(report, params);
        String query = evaluateExpressions(report, params);

        logger.debug("Execute report {}\n{}\nparams={}", report.reportId, query, Arrays.toString(params));
        int totalRowCount = 0;
        for (String subQuery : query.split(";;;")) {
            List<ReportData> rows;
            try {
                rows = queryRunner.execute(subQuery, params);
            } catch (Exception e) {
                throw new ApplicationFailureException("error executing query [" + subQuery + "]", e);
            }
            if ((rows == null) || rows.isEmpty()) continue;

            totalRowCount += rows.size();
            List<ReportField> fields = report.getFieldsOrdered();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                ReportData row = rows.get(rowIndex);
                for (ReportField field : fields) {
                    field.putValue(row.getValue(field.columnName), rowIndex, rows.size());
                }
            }
        }
        return totalRowCount;
    }

    private static void convertDateParameters(Report report, Object[] params) {
        if ((report.params == null) || (params == null)) return;
        for (ReportParam param : report.params) {
            if ((param.dataType == ReportParam.DATATYPE_DATETIME) && (params[param.index] instanceof Long time)) {
                params[param.index] = new Timestamp(time);
            }
        }
    }

    private static String evaluateExpressions(Report report, Object[] params) {
        StringBuilder query = new StringBuilder(report.sqlQuery);
        int start = query.indexOf("$J{");
        if (start < 0) return query.toString();

        HashMap<String, Object> values = new HashMap<>();
        if ((report.params != null) && (params != null)) {
            for (ReportParam param : report.params) values.put(param.name, params[param.index]);
        }
        do {
            int end = query.indexOf("$J}", start + 3);
            if (end < 0) throw new ApplicationFailureException("malformed JEXL expression");
            String expression = query.substring(start + 3, end);
            try {
                Object result = JEXL.createExpression(expression).evaluate(new MapContext(values));
                if (result == null) query.delete(start, end + 3);
                else query.replace(start, end + 3, result.toString());
            } catch (Exception e) {
                throw new ApplicationFailureException("expression evaluation error [" + expression + "]", e);
            }
            start = query.indexOf("$J{", start + 1);
        } while (start >= 0);
        return query.toString();
    }
}
