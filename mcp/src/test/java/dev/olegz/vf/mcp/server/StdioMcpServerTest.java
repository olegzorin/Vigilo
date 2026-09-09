package dev.olegz.vf.mcp.server;

import java.io.ByteArrayInputStream;
import java.io.PrintStream;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.mcp.Json;
import dev.olegz.vf.mcp.db.DatabaseService;
import dev.olegz.vf.mcp.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocol-level tests for {@link StdioMcpServer#handle(String)}. These exercise the methods that do
 * not touch the database (initialize / ping / tools/list / notifications / errors); the
 * {@link DatabaseService} is built from in-memory connection properties and never connected.
 */
class StdioMcpServerTest {

    private static StdioMcpServer server;

    @BeforeAll
    static void setUp() {
        // Connection properties so DatabaseService can build its pool (no connection is opened here).
        PropertyStore.set("jdbc.url", "jdbc:postgresql://localhost:5432/none");
        PropertyStore.set("jdbc.user", "test");
        PropertyStore.set("jdbc.password", "test");

        ToolRegistry registry = new ToolRegistry(new DatabaseService());
        // stdin/stdout are unused by handle(); pass harmless streams.
        server = new StdioMcpServer(new ByteArrayInputStream(new byte[0]),
            new PrintStream(java.io.OutputStream.nullOutputStream()), registry);
    }

    @Test
    void initializeReturnsServerInfoAndCapabilities() {
        JsonNode resp = Json.parse(server.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                + "\"params\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{}}}"));

        assertEquals("2.0", resp.get("jsonrpc").asString());
        assertEquals(1, resp.get("id").intValue());
        JsonNode result = resp.get("result");
        assertEquals("2024-11-05", result.get("protocolVersion").asString());
        assertEquals("vf-postgresql-mcp", result.get("serverInfo").get("name").asString());
        assertTrue(result.get("capabilities").has("tools"));
    }

    @Test
    void toolsListReturnsAllFourTools() {
        JsonNode resp = Json.parse(server.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}"));

        JsonNode tools = resp.get("result").get("tools");
        assertEquals(4, tools.size());

        StringBuilder names = new StringBuilder();
        for (int i = 0; i < tools.size(); i++) {
            names.append(tools.get(i).get("name").asString()).append(' ');
        }
        String all = names.toString();
        assertTrue(all.contains("list_tables"), all);
        assertTrue(all.contains("describe_table"), all);
        assertTrue(all.contains("read_query"), all);
        assertTrue(all.contains("write_query"), all);
    }

    @Test
    void pingReturnsResult() {
        JsonNode resp = Json.parse(server.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"ping\"}"));
        assertTrue(resp.has("result"));
    }

    @Test
    void notificationProducesNoResponse() {
        assertNull(server.handle("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}"));
    }

    @Test
    void unknownMethodReturnsMethodNotFound() {
        JsonNode resp = Json.parse(server.handle(
            "{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"no/such/method\"}"));
        assertEquals(-32601, resp.get("error").get("code").intValue());
    }

    @Test
    void malformedJsonReturnsParseError() {
        JsonNode resp = Json.parse(server.handle("{ this is not json"));
        assertEquals(-32700, resp.get("error").get("code").intValue());
    }
}
