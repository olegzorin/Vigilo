package dev.olegz.vf.mcp;

import java.io.PrintStream;

import dev.olegz.vf.common.props.PropertyStore;
import dev.olegz.vf.mcp.db.DatabaseService;
import dev.olegz.vf.mcp.server.StdioMcpServer;
import dev.olegz.vf.mcp.tool.ToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the MCP server. Speaks JSON-RPC over stdio: an MCP client (e.g. Claude
 * Desktop/Code) launches this process and exchanges messages over stdin/stdout, getting read and
 * write access to the local PostgreSQL database.
 * <p>
 * Database connection settings (jdbc.url/user/password) are resolved by the project's
 * {@code PropertyStore} from {@code VF_HOME/config/properties} (and {@code VF_KEK} when the
 * password is encrypted) — PostgreSQL must already be running.
 */
public final class Application {
    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private Application() {
    }

    public static void main(String[] args) {
        // stdout is the JSON-RPC channel. Keep a reference to it, then redirect System.out to stderr
        // so any stray bootstrap/library println (VigiloEnvironment, PropertyStore, drivers) cannot corrupt the
        // protocol stream. All protocol frames are written through 'protocolOut'.
        PrintStream protocolOut = System.out;
        System.setOut(System.err);

        try {
            PropertyStore.start();
            try (DatabaseService db = new DatabaseService()) {
                db.validate(); // fail fast on bad VF_HOME / jdbc.* / unreachable DB

                ToolRegistry registry = new ToolRegistry(db);
                StdioMcpServer server = new StdioMcpServer(System.in, protocolOut, registry);

                logger.info("MCP server started (stdio transport)");
                server.run(); // blocks until stdin EOF
                logger.info("MCP server stopped (stdin closed)");
            }
        } catch (Exception e) {
            logger.error("Fatal error in MCP server", e);
            System.err.println("Fatal error in MCP server: " + e.getMessage());
            System.exit(1);
        }
    }
}
