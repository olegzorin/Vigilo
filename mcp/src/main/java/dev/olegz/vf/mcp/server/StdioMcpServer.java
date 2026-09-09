package dev.olegz.vf.mcp.server;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.olegz.vf.mcp.Json;
import dev.olegz.vf.mcp.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/**
 * Minimal MCP server speaking JSON-RPC 2.0 over stdio: one JSON object per line (UTF-8) read from
 * the input stream, one response line written to the output stream. Requests (which carry an
 * {@code id}) get a response; notifications (no {@code id}) do not.
 * <p>
 * Implements the subset an MCP tools server needs: {@code initialize}, {@code ping},
 * {@code tools/list} and {@code tools/call} (plus {@code notifications/*}).
 */
public final class StdioMcpServer {
    private static final Logger logger = LoggerFactory.getLogger(StdioMcpServer.class);

    private final InputStream in;
    private final PrintStream out;
    private final ToolRegistry registry;

    public StdioMcpServer(InputStream in, PrintStream out, ToolRegistry registry) {
        this.in = in;
        this.out = out;
        this.registry = registry;
    }

    /** Read-dispatch-write loop. Returns when the input stream reaches EOF (client closed stdin). */
    public void run() throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            String response = handle(line);
            if (response != null) {
                out.println(response);
                out.flush();
            }
        }
    }

    /** Handle a single JSON-RPC line. Returns the response line, or {@code null} for notifications. */
    String handle(String line) {
        JsonNode request;
        try {
            request = Json.parse(line);
        } catch (Exception e) {
            logger.warn("Failed to parse JSON-RPC message: {}", e.getMessage());
            return Json.write(JsonRpc.error(null, JsonRpc.PARSE_ERROR, "Parse error"));
        }

        JsonNode idNode = request.get("id");
        boolean isNotification = (idNode == null) || idNode.isNull();

        JsonNode methodNode = request.get("method");
        if (methodNode == null || !methodNode.isValueNode()) {
            return isNotification ? null
                : Json.write(JsonRpc.error(idNode, JsonRpc.INVALID_REQUEST, "Missing 'method'"));
        }
        String method = methodNode.asString();
        JsonNode params = request.get("params");

        try {
            Map<String, Object> response = dispatch(method, params, idNode);
            if (isNotification) {
                return null;
            }
            return response == null ? null : Json.write(response);
        } catch (Exception e) {
            logger.error("Error handling method '{}'", method, e);
            return isNotification ? null
                : Json.write(JsonRpc.error(idNode, JsonRpc.INTERNAL_ERROR, String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> dispatch(String method, JsonNode params, JsonNode idNode) {
        switch (method) {
            case JsonRpc.M_INITIALIZE:
                return JsonRpc.result(idNode, initializeResult(params));
            case JsonRpc.M_PING:
                return JsonRpc.result(idNode, new LinkedHashMap<>());
            case JsonRpc.M_TOOLS_LIST: {
                Map<String, Object> result = new LinkedHashMap<>(1);
                result.put("tools", registry.listTools());
                return JsonRpc.result(idNode, result);
            }
            case JsonRpc.M_TOOLS_CALL:
                return JsonRpc.result(idNode, toolsCallResult(params));
            default:
                // Unknown notifications (e.g. notifications/initialized) are dropped by the caller.
                return JsonRpc.error(idNode, JsonRpc.METHOD_NOT_FOUND, "Method not found: " + method);
        }
    }

    private Map<String, Object> initializeResult(JsonNode params) {
        String protocolVersion = JsonRpc.DEFAULT_PROTOCOL_VERSION;
        if (params != null) {
            JsonNode pv = params.get("protocolVersion");
            if (pv != null && pv.isValueNode()) {
                protocolVersion = pv.asString();
            }
        }

        Map<String, Object> serverInfo = new LinkedHashMap<>(2);
        serverInfo.put("name", JsonRpc.SERVER_NAME);

        Map<String, Object> capabilities = new LinkedHashMap<>(1);
        capabilities.put("tools", new LinkedHashMap<>());

        Map<String, Object> result = new LinkedHashMap<>(3);
        result.put("protocolVersion", protocolVersion);
        result.put("capabilities", capabilities);
        result.put("serverInfo", serverInfo);
        return result;
    }

    private Map<String, Object> toolsCallResult(JsonNode params) {
        String name = null;
        JsonNode arguments = null;
        if (params != null) {
            JsonNode nameNode = params.get("name");
            if (nameNode != null && nameNode.isValueNode()) {
                name = nameNode.asString();
            }
            arguments = params.get("arguments");
        }

        ToolRegistry.ToolResult toolResult = registry.call(name, arguments);

        Map<String, Object> textContent = new LinkedHashMap<>(2);
        textContent.put("type", "text");
        textContent.put("text", toolResult.text());
        List<Object> content = new ArrayList<>(1);
        content.add(textContent);

        Map<String, Object> result = new LinkedHashMap<>(2);
        result.put("content", content);
        result.put("isError", toolResult.isError());
        return result;
    }
}
