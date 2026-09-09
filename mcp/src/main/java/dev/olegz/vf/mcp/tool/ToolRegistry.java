package dev.olegz.vf.mcp.tool;

import java.util.*;

import dev.olegz.vf.mcp.Json;
import dev.olegz.vf.mcp.db.DatabaseService;
import tools.jackson.databind.JsonNode;

/**
 * Defines the MCP tools (name, description, JSON input schema) and dispatches {@code tools/call}
 * to {@link DatabaseService}. Tool failures are returned as {@link ToolResult} with
 * {@code isError=true} text rather than thrown, so the MCP client sees a usable message.
 */
public final class ToolRegistry {

    /** Outcome of a tool call: text payload and whether it represents an error. */
    public record ToolResult(String text, boolean isError) {
        static ToolResult ok(Object value) {
            return new ToolResult(Json.write(value), false);
        }

        static ToolResult error(String message) {
            return new ToolResult(message, true);
        }
    }

    private final DatabaseService db;

    public ToolRegistry(DatabaseService db) {
        this.db = db;
    }

    /** Tool definitions advertised in {@code tools/list}. */
    public List<Map<String, Object>> listTools() {
        List<Map<String, Object>> tools = new ArrayList<>(4);
        tools.add(tool("list_tables",
            "List the tables in the connected PostgreSQL database (current schema).",
            objectSchema(noProperties(), new String[0])));
        tools.add(tool("describe_table",
            "Describe a table: column name, type, nullability, key and default value.",
            objectSchema(oneProperty("table", stringProperty("Name of the table to describe.")),
                new String[]{"table"})));
        tools.add(tool("read_query",
            "Run a read-only SQL query (SELECT / WITH / EXPLAIN) and return the rows.",
            objectSchema(oneProperty("sql", stringProperty("The SQL query to run. Must be a read statement.")),
                new String[]{"sql"})));
        tools.add(tool("write_query",
            "Run a write SQL statement: INSERT / UPDATE / DELETE, or DDL (CREATE / ALTER / DROP / "
                + "TRUNCATE). Returns the number of affected rows.",
            objectSchema(oneProperty("sql", stringProperty("The SQL statement to execute. Must not be a SELECT.")),
                new String[]{"sql"})));
        return tools;
    }

    /** Dispatch a {@code tools/call}. Never throws — errors come back as {@code isError} results. */
    public ToolResult call(String name, JsonNode arguments) {
        if (name == null) {
            return ToolResult.error("Missing tool name");
        }
        try {
            return switch (name) {
                case "list_tables" -> ToolResult.ok(db.listTables());
                case "describe_table" -> ToolResult.ok(db.describeTable(requiredString(arguments, "table")));
                case "read_query" -> readQuery(requiredString(arguments, "sql"));
                case "write_query" -> writeQuery(requiredString(arguments, "sql"));
                default -> ToolResult.error("Unknown tool: " + name);
            };
        } catch (IllegalArgumentException e) {
            return ToolResult.error(e.getMessage());
        } catch (Exception e) {
            return ToolResult.error(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private ToolResult readQuery(String sql) throws Exception {
        if (!SqlStatements.isRead(sql)) {
            return ToolResult.error("read_query only accepts read statements "
                + "(SELECT / WITH / EXPLAIN). Use write_query for writes.");
        }
        return ToolResult.ok(db.read(sql));
    }

    private ToolResult writeQuery(String sql) throws Exception {
        if (SqlStatements.firstKeyword(sql).isEmpty()) {
            return ToolResult.error("Empty SQL statement.");
        }
        if (SqlStatements.isRead(sql)) {
            return ToolResult.error("write_query does not accept read statements. "
                + "Use read_query for SELECT / WITH / EXPLAIN.");
        }
        Map<String, Object> result = new LinkedHashMap<>(1);
        result.put("affectedRows", db.write(sql));
        return ToolResult.ok(result);
    }

    private static String requiredString(JsonNode arguments, String field) {
        JsonNode node = (arguments == null) ? null : arguments.get(field);
        if (node == null || node.isNull() || !node.isValueNode()) {
            throw new IllegalArgumentException("Missing required argument: " + field);
        }
        String value = node.asString();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Argument '" + field + "' must not be empty");
        }
        return value;
    }

    // --- JSON Schema builders ---------------------------------------------------------------

    private static Map<String, Object> tool(String name, String description, Map<String, Object> inputSchema) {
        Map<String, Object> t = new LinkedHashMap<>(3);
        t.put("name", name);
        t.put("description", description);
        t.put("inputSchema", inputSchema);
        return t;
    }

    private static Map<String, Object> objectSchema(Map<String, Object> properties, String[] required) {
        Map<String, Object> schema = new LinkedHashMap<>(3);
        schema.put("type", "object");
        schema.put("properties", properties);
        if (required.length > 0) {
            List<String> req = new ArrayList<>(required.length);
            Collections.addAll(req, required);
            schema.put("required", req);
        }
        return schema;
    }

    private static Map<String, Object> noProperties() {
        return new LinkedHashMap<>();
    }

    private static Map<String, Object> oneProperty(String name, Map<String, Object> property) {
        Map<String, Object> properties = new LinkedHashMap<>(1);
        properties.put(name, property);
        return properties;
    }

    private static Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>(2);
        property.put("type", "string");
        property.put("description", description);
        return property;
    }
}
