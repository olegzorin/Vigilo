package dev.olegz.vf.mcp;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * JSON parse/serialize helper for the MCP server.
 * <p>
 * Uses a plain Jackson 3 {@link JsonMapper} that <b>includes null values</b> — unlike the shared
 * {@code shared.map.StringMapper}, which is configured with {@code NON_NULL} inclusion. Null
 * inclusion matters here so a null column value in a query result is rendered as an explicit
 * {@code null} (the client can tell the column is null, not absent) and so JSON-RPC {@code id}
 * fields round-trip faithfully.
 */
public final class Json {
    private Json() {
    }

    private static final JsonMapper MAPPER = JsonMapper.builder()
        .disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
        .build();

    /** Parse a JSON document. Throws {@link IllegalArgumentException} on malformed input. */
    public static JsonNode parse(String text) {
        try {
            return MAPPER.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON: " + e.getMessage(), e);
        }
    }

    /** Serialize a value (Maps/Lists/scalars/{@code JsonNode}) to a single-line JSON string. */
    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialization failed: " + e.getMessage(), e);
        }
    }
}
