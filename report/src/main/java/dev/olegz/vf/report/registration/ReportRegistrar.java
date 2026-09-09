package dev.olegz.vf.report.registration;

import java.util.List;

import org.springframework.transaction.support.TransactionOperations;

import dev.olegz.vf.report.dao.mapper.ReportsMapper;
import dev.olegz.vf.report.domain.Report;

/** Registers complete validated report definitions through the report MyBatis mapper. */
public final class ReportRegistrar {
    private final ReportsMapper reportsMapper;
    private final TransactionOperations transactions;

    public ReportRegistrar(ReportsMapper reportsMapper, TransactionOperations transactions) {
        this.reportsMapper = reportsMapper;
        this.transactions = transactions;
    }

    public void register(List<LoadedReportDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            throw new IllegalArgumentException("No report definitions to register");
        }
        transactions.executeWithoutResult(_ -> definitions.forEach(this::register));
    }

    private void register(LoadedReportDefinition definition) {
        Report report = definition.report();
        if (reportsMapper.updateReportDefinition(report, definition.sql()) == 0) {
            reportsMapper.insertReportDefinition(report, definition.sql());
        }

        reportsMapper.deleteReportParams(report.reportId);
        report.params.forEach(reportsMapper::insertReportParam);

        reportsMapper.deleteReportFields(report.reportId);
        report.fields.forEach(reportsMapper::insertReportField);

        reportsMapper.deleteReportMetadata(report.reportId);
        report.metadata.forEach(reportsMapper::insertReportMetadata);
    }
}
