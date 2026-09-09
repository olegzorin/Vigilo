package dev.olegz.vf.report.registration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.olegz.vf.common.ShutdownManager;
import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.registry.config.DataSourceConfig;
import dev.olegz.vf.report.ReportConfig;
import dev.olegz.vf.report.dao.mapper.ReportsMapper;

/**
 * Deployment-only command for validating and transactionally registering report definitions.
 * <p>
 * From the repository root:
 * <pre>
 * mvn -q -pl report -am -DskipTests -Pregister-reports verify
 * mvn -q -pl report -am -DskipTests -Pregister-reports verify -Dexec.args="--validate-only"
 * mvn -q -pl report -am -DskipTests -Pregister-reports verify \
 *     -Dexec.args="--definitions /path/to/report/definitions"
 * </pre>
 * Database settings are read through {@link PropertyStore}; credentials never need to be passed
 * on the command line.
 */
public final class RegisterReportsTool {
    private static final Path ROOT_DEFAULT = Path.of("report/database/definitions");
    private static final Path MODULE_DEFAULT = Path.of("database/definitions");

    private RegisterReportsTool() {
    }

    public static void main(String[] args) {
        try {
            Arguments options = Arguments.parse(args);
            Path directory = options.definitions() == null ? defaultDirectory() : options.definitions();
            List<LoadedReportDefinition> reports = new ReportDefinitionLoader().load(directory);
            if (options.validateOnly()) {
                System.out.println("Validated " + reports.size() + " report definitions in " + directory.toAbsolutePath());
                return;
            }

            PropertyStore.start();
            try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(DataSourceConfig.class, ReportConfig.class)) {
                ReportsMapper reportsMapper = context.getBean(ReportsMapper.class);
                TransactionTemplate transactions =
                    new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                new ReportRegistrar(reportsMapper, transactions).register(reports);
            }
            System.out.println("Registered " + reports.size() + " reports from " + directory.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("Failed to register reports: " + e.getMessage());
            System.exit(1);
        } finally {
            ShutdownManager.shutdown();
        }
    }

    private static Path defaultDirectory() {
        if (Files.isDirectory(ROOT_DEFAULT)) return ROOT_DEFAULT;
        if (Files.isDirectory(MODULE_DEFAULT)) return MODULE_DEFAULT;
        return ROOT_DEFAULT;
    }

    record Arguments(Path definitions, boolean validateOnly) {
        static Arguments parse(String[] args) {
            Path definitions = null;
            boolean validateOnly = false;
            for (int i = 0; i < args.length; i++) {
                String argument = args[i];
                if ("--validate-only".equals(argument)) {
                    validateOnly = true;
                } else if (argument.startsWith("--definitions=")) {
                    definitions = Path.of(argument.substring("--definitions=".length()));
                } else if ("--definitions".equals(argument) && i + 1 < args.length) {
                    definitions = Path.of(args[++i]);
                } else {
                    throw new IllegalArgumentException("Unknown or incomplete argument: " + argument);
                }
            }
            return new Arguments(definitions, validateOnly);
        }
    }
}
