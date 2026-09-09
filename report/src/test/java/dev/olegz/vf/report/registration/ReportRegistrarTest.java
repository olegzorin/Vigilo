package dev.olegz.vf.report.registration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import dev.olegz.vf.report.dao.mapper.ReportsMapper;
import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportDataType;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportMetadata;
import dev.olegz.vf.report.domain.ReportParam;

class ReportRegistrarTest {
    @Test
    void insertsCompleteDefinitionInOneTransaction() {
        RecordingMapper mapper = new RecordingMapper(null);
        RecordingTransactions transactions = new RecordingTransactions();

        new ReportRegistrar(mapper.proxy(), transactions).register(List.of(definition()));

        assertTrue(transactions.completed);
        assertFalse(transactions.failed);
        assertEquals(List.of(
            "updateReportDefinition", "insertReportDefinition",
            "deleteReportParams", "insertReportParam",
            "deleteReportFields", "insertReportField",
            "deleteReportMetadata", "insertReportMetadata"), mapper.calls);
    }

    @Test
    void existingReportIsUpdatedWithoutInsert() {
        RecordingMapper mapper = new RecordingMapper(null);
        mapper.updateCount = 1;

        new ReportRegistrar(mapper.proxy(), new RecordingTransactions()).register(List.of(definition()));

        assertFalse(mapper.calls.contains("insertReportDefinition"));
    }

    @Test
    void propagatesFailureThroughTransactionBoundary() {
        RecordingMapper mapper = new RecordingMapper("insertReportField");
        RecordingTransactions transactions = new RecordingTransactions();

        assertThrows(IllegalStateException.class,
            () -> new ReportRegistrar(mapper.proxy(), transactions).register(List.of(definition())));

        assertFalse(transactions.completed);
        assertTrue(transactions.failed);
        assertFalse(mapper.calls.contains("deleteReportMetadata"));
    }

    @Test
    void rejectsEmptyDefinitionsBeforeStartingTransaction() {
        RecordingTransactions transactions = new RecordingTransactions();

        assertThrows(IllegalArgumentException.class,
            () -> new ReportRegistrar(new RecordingMapper(null).proxy(), transactions).register(List.of()));

        assertFalse(transactions.started);
    }

    private static LoadedReportDefinition definition() {
        Report report = new Report();
        report.reportId = 7;
        report.reportName = "TestReport";
        report.reportType = Report.TYPE_SUMMARY;
        report.displayName = "Test report";
        report.description = "Description";

        ReportParam parameter = new ReportParam();
        parameter.reportId = 7;
        parameter.index = 0;
        parameter.name = "organizationId";
        parameter.dataType = ReportDataType.INTEGER.databaseValue();
        parameter.required = true;
        parameter.displayName = "Organization";
        report.params = List.of(parameter);

        ReportField field = new ReportField();
        field.reportId = 7;
        field.index = 1;
        field.name = "count";
        field.columnName = "n1";
        field.dataType = ReportDataType.INTEGER.databaseValue();
        field.displayName = "Count";
        report.fields = List.of(field);

        ReportMetadata metadata = new ReportMetadata();
        metadata.reportId = 7;
        metadata.name = "total";
        metadata.func_index = 0;
        metadata.field_index = 1;
        report.metadata = List.of(metadata);

        return new LoadedReportDefinition(report, Path.of("7_Test.report.yaml"), "SELECT #{p0} AS n1");
    }

    private static final class RecordingMapper implements InvocationHandler {
        private final String failureMethod;
        private final List<String> calls = new ArrayList<>();
        private int updateCount;

        private RecordingMapper(String failureMethod) {
            this.failureMethod = failureMethod;
        }

        ReportsMapper proxy() {
            return (ReportsMapper) Proxy.newProxyInstance(
                ReportsMapper.class.getClassLoader(), new Class<?>[] {ReportsMapper.class}, this);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            calls.add(method.getName());
            if (method.getName().equals(failureMethod)) throw new IllegalStateException("simulated mapper failure");
            if (method.getName().equals("updateReportDefinition")) return updateCount;
            return defaultValue(method.getReturnType());
        }

        private static Object defaultValue(Class<?> type) {
            if (!type.isPrimitive()) return null;
            if (type == boolean.class) return false;
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            if (type == char.class) return '\0';
            return null;
        }
    }

    private static final class RecordingTransactions implements TransactionOperations {
        private boolean started;
        private boolean completed;
        private boolean failed;

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            started = true;
            TransactionStatus status = new SimpleTransactionStatus();
            try {
                T result = action.doInTransaction(status);
                completed = true;
                return result;
            } catch (RuntimeException | Error e) {
                failed = true;
                throw e;
            }
        }
    }
}
