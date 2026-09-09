package dev.olegz.vf.mcp.server;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 + MCP constants and small response builders.
 * Responses are built as ordered {@link Map}s and serialized by {@link dev.olegz.vf.mcp.Json}.
 */
public final class JsonRpc {
    private JsonRpc() {
    }

    public static final String VERSION = "2.0";

    /** MCP protocol version this server speaks by default (the client's value is echoed when provided). */
    public static final String DEFAULT_PROTOCOL_VERSION = "2024-11-05";
    public static final String SERVER_NAME = "vf-postgresql-mcp";

    // Methods
    public static final String M_INITIALIZE = "initialize";
    public static final String M_PING = "ping";
    public static final String M_TOOLS_LIST = "tools/list";
    public static final String M_TOOLS_CALL = "tools/call";

    // JSON-RPC error codes
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INTERNAL_ERROR = -32603;

    /** {@code {"jsonrpc":"2.0","id":<id>,"result":<result>}} */
    public static Map<String, Object> result(JsonNode id, Object result) {
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("jsonrpc", VERSION);
        m.put("id", id);
        m.put("result", result);
        return m;
    }

    /** {@code {"jsonrpc":"2.0","id":<id|null>,"error":{"code":..,"message":..}}} */
    public static Map<String, Object> error(JsonNode id, int code, String message) {
        Map<String, Object> err = new LinkedHashMap<>(2);
        err.put("code", code);
        err.put("message", message);
        Map<String, Object> m = new LinkedHashMap<>(4);
        m.put("jsonrpc", VERSION);
        m.put("id", id);
        m.put("error", err);
        return m;
    }
}
