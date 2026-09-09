package dev.olegz.vf.report.registration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.olegz.vf.report.domain.Report;
import dev.olegz.vf.report.domain.ReportDataType;
import dev.olegz.vf.report.domain.ReportField;
import dev.olegz.vf.report.domain.ReportMetadata;
import dev.olegz.vf.report.domain.ReportParam;
import tools.jackson.databind.JsonNode;
import tools.jackson.dataformat.yaml.YAMLMapper;

/** Maps YAML registration documents into the report domain model and validates their query SQL. */
public final class ReportDefinitionLoader {
    private static final String DEFINITION_SUFFIX = ".report.yaml";
    private static final int MAX_QUERY_LENGTH = 10_000;
    private static final int MAX_PARAMETERS = 10;
    private static final Pattern PARAMETER_REFERENCE = Pattern.compile("#\\{p(\\d+)}");
    private static final Pattern FILE_ID = Pattern.compile("^(\\d+)_.*\\.report\\.yaml$");
    private static final Pattern READ_QUERY = Pattern.compile("(?is)^(SELECT|WITH)\\b.*");
    private static final Set<String> REPORT_PROPERTIES = Set.of(
        "id", "name", "type", "displayName", "description", "query", "parameters", "fields", "metadata");
    private static final Set<String> PARAMETER_PROPERTIES = Set.of(
        "name", "type", "required", "displayName", "description", "placeholder");
    private static final Set<String> FIELD_PROPERTIES = Set.of(
        "name", "column", "type", "displayName", "description");
    private static final Set<String> METADATA_PROPERTIES = Set.of(
        "name", "function", "field", "description");

    private final YAMLMapper mapper = YAMLMapper.builder().build();

    public List<LoadedReportDefinition> load(Path directory) {
        Path root = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Report definitions directory does not exist: " + root);
        }

        List<Path> sources;
        try (Stream<Path> files = Files.list(root)) {
            sources = files.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(DEFINITION_SUFFIX))
                .sorted()
                .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot list report definitions in " + root, e);
        }
        if (sources.isEmpty()) throw new IllegalArgumentException("No " + DEFINITION_SUFFIX + " files in " + root);

        List<LoadedReportDefinition> reports = new ArrayList<>(sources.size());
        Set<Integer> ids = new HashSet<>();
        for (Path source : sources) {
            LoadedReportDefinition loaded = load(root, source);
            if (!ids.add(loaded.report().reportId)) {
                throw invalid(source, "duplicate report id " + loaded.report().reportId);
            }
            reports.add(loaded);
        }
        return List.copyOf(reports);
    }

    private LoadedReportDefinition load(Path root, Path source) {
        try {
            JsonNode document = mapper.readTree(source);
            if (document == null || !document.isObject()) throw invalid(source, "YAML document must be an object");
            rejectUnknown(source, "report", document, REPORT_PROPERTIES);

            Report report = readReport(source, document);
            validateDefinitionFile(source, report.reportId);
            String queryName = requiredText(source, document, "query", 250);
            Path query = resolveQuery(root, source, queryName);
            String sql = Files.readString(query).trim();
            validate(source, report, sql);
            return new LoadedReportDefinition(report, source, sql);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot load report definition " + source, e);
        }
    }

    private static Report readReport(Path source, JsonNode document) {
        Report report = new Report();
        report.reportId = requiredInt(source, document, "id");
        report.reportName = requiredText(source, document, "name", 100);
        report.reportType = Report.Type.fromName(requiredText(source, document, "type", 20)).databaseValue();
        report.displayName = requiredText(source, document, "displayName", 150);
        report.description = optionalText(source, document, "description", 250);
        report.params = readParameters(source, document.get("parameters"), report.reportId);
        report.fields = readFields(source, document.get("fields"), report.reportId);
        report.metadata = readMetadata(source, document.get("metadata"), report.reportId);
        return report;
    }

    private static List<ReportParam> readParameters(Path source, JsonNode array, int reportId) {
        if (array == null || array.isNull()) return List.of();
        requireArray(source, "parameters", array);
        List<ReportParam> parameters = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonNode node = array.get(index);
            requireObject(source, "parameter " + index, node);
            rejectUnknown(source, "parameter " + index, node, PARAMETER_PROPERTIES);
            ReportParam parameter = new ReportParam();
            parameter.reportId = reportId;
            parameter.index = index;
            parameter.name = requiredText(source, node, "name", 30);
            parameter.dataType = readDataType(source, node);
            parameter.required = optionalBoolean(source, node, "required", false);
            parameter.displayName = requiredText(source, node, "displayName", 60);
            parameter.description = optionalText(source, node, "description", 250);
            parameter.placeholder = optionalText(source, node, "placeholder", 100);
            parameters.add(parameter);
        }
        return List.copyOf(parameters);
    }

    private static List<ReportField> readFields(Path source, JsonNode array, int reportId) {
        if (array == null || array.isNull()) return List.of();
        requireArray(source, "fields", array);
        List<ReportField> fields = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonNode node = array.get(index);
            requireObject(source, "field " + index, node);
            rejectUnknown(source, "field " + index, node, FIELD_PROPERTIES);
            ReportField field = new ReportField();
            field.reportId = reportId;
            field.index = index + 1;
            field.name = requiredText(source, node, "name", 50);
            field.columnName = requiredText(source, node, "column", 30);
            field.dataType = readDataType(source, node);
            field.displayName = requiredText(source, node, "displayName", 90);
            field.description = optionalText(source, node, "description", 150);
            fields.add(field);
        }
        return List.copyOf(fields);
    }

    private static List<ReportMetadata> readMetadata(Path source, JsonNode array, int reportId) {
        if (array == null || array.isNull()) return List.of();
        requireArray(source, "metadata", array);
        List<ReportMetadata> metadata = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            JsonNode node = array.get(index);
            requireObject(source, "metadata " + index, node);
            rejectUnknown(source, "metadata " + index, node, METADATA_PROPERTIES);
            ReportMetadata value = new ReportMetadata();
            value.reportId = reportId;
            value.name = requiredText(source, node, "name", 50);
            value.func_index = (byte) requiredInt(source, node, "function");
            value.field_index = requiredInt(source, node, "field");
            value.description = optionalText(source, node, "description", 255);
            metadata.add(value);
        }
        return List.copyOf(metadata);
    }

    private static int readDataType(Path source, JsonNode node) {
        String type = requiredText(source, node, "type", 20);
        try {
            return ReportDataType.fromName(type).databaseValue();
        } catch (IllegalArgumentException e) {
            throw invalid(source, e.getMessage());
        }
    }

    private static Path resolveQuery(Path root, Path source, String queryName) {
        Path query = root.resolve(queryName).normalize();
        if (!query.startsWith(root)) throw invalid(source, "query must stay inside " + root);
        if (!Files.isRegularFile(query)) throw invalid(source, "query file does not exist: " + queryName);
        return query;
    }

    private static void validateDefinitionFile(Path source, int reportId) {
        Matcher matcher = FILE_ID.matcher(source.getFileName().toString());
        if (!matcher.matches()) throw invalid(source, "filename must start with the report id and underscore");
        if (reportId <= 0) throw invalid(source, "id must be positive");
        if (Integer.parseInt(matcher.group(1)) != reportId) {
            throw invalid(source, "filename id does not match id " + reportId);
        }
    }

    private static void validate(Path source, Report report, String sql) {
        if (sql.isEmpty()) throw invalid(source, "query is empty");
        if (sql.length() > MAX_QUERY_LENGTH) throw invalid(source, "query exceeds " + MAX_QUERY_LENGTH + " characters");
        if (report.params.size() > MAX_PARAMETERS) {
            throw invalid(source, "at most " + MAX_PARAMETERS + " parameters are supported");
        }
        validateParameters(source, report, sql);
        validateFields(source, report);
        validateMetadata(source, report);
        for (String query : sql.split(";;;", -1)) {
            if (!READ_QUERY.matcher(query.strip()).matches()) {
                throw invalid(source, "every query segment must start with SELECT or WITH");
            }
        }
    }

    private static void validateParameters(Path source, Report report, String sql) {
        Set<String> names = new HashSet<>();
        for (ReportParam parameter : report.params) {
            if (!names.add(parameter.name)) throw invalid(source, "duplicate parameter name " + parameter.name);
        }
        Matcher references = PARAMETER_REFERENCE.matcher(sql);
        while (references.find()) {
            int index = Integer.parseInt(references.group(1));
            if (index >= report.params.size()) {
                throw invalid(source, "query references p" + index + " but only "
                    + report.params.size() + " parameters are declared");
            }
        }
    }

    private static void validateFields(Path source, Report report) {
        if (report.fields.isEmpty()) throw invalid(source, "at least one field is required");
        Set<String> names = new HashSet<>();
        Set<String> columns = new HashSet<>();
        for (ReportField field : report.fields) {
            if (!names.add(field.name)) throw invalid(source, "duplicate field name " + field.name);
            if (!columns.add(field.columnName)) throw invalid(source, "duplicate field column " + field.columnName);
        }
    }

    private static void validateMetadata(Path source, Report report) {
        Set<String> names = new HashSet<>();
        for (ReportMetadata metadata : report.metadata) {
            if (!names.add(metadata.name)) throw invalid(source, "duplicate metadata name " + metadata.name);
            if (metadata.func_index < 0) throw invalid(source, "metadata function must not be negative");
            if (metadata.field_index < 1 || metadata.field_index > report.fields.size()) {
                throw invalid(source, "metadata field index is out of range: " + metadata.field_index);
            }
        }
    }

    private static void rejectUnknown(Path source, String object, JsonNode node, Set<String> allowed) {
        for (String property : node.propertyNames()) {
            if (!allowed.contains(property)) throw invalid(source, "unknown " + object + " property: " + property);
        }
    }

    private static String requiredText(Path source, JsonNode object, String property, int maxLength) {
        String value = optionalText(source, object, property, maxLength);
        if (value == null || value.isBlank()) throw invalid(source, property + " is required");
        return value;
    }

    private static String optionalText(Path source, JsonNode object, String property, int maxLength) {
        JsonNode value = object.get(property);
        if (value == null || value.isNull()) return null;
        if (!value.isString()) throw invalid(source, property + " must be text");
        String text = value.stringValue();
        if (text.length() > maxLength) throw invalid(source, property + " exceeds " + maxLength + " characters");
        return text;
    }

    private static int requiredInt(Path source, JsonNode object, String property) {
        JsonNode value = object.get(property);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid(source, property + " must be an integer");
        }
        return value.intValue();
    }

    private static boolean optionalBoolean(Path source, JsonNode object, String property, boolean defaultValue) {
        JsonNode value = object.get(property);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.isBoolean()) throw invalid(source, property + " must be boolean");
        return value.booleanValue();
    }

    private static void requireArray(Path source, String property, JsonNode node) {
        if (!node.isArray()) throw invalid(source, property + " must be an array");
    }

    private static void requireObject(Path source, String property, JsonNode node) {
        if (node == null || !node.isObject()) throw invalid(source, property + " must be an object");
    }

    private static IllegalArgumentException invalid(Path source, String message) {
        return new IllegalArgumentException(source.getFileName() + ": " + message);
    }
}
