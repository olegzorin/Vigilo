package dev.olegz.vf.mcp.db;

import java.sql.*;
import java.time.temporal.Temporal;
import java.util.*;

import dev.olegz.vf.common.props.PropertyStore;
import org.apache.tomcat.jdbc.pool.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thin data-access layer for the MCP tools. Builds its own small {@code tomcat-jdbc} pool from the
 * project's {@code jdbc.*} properties (resolved by {@link PropertyStore} from {@code VF_HOME}),
 * and runs raw JDBC. It does <b>not</b> depend on {@code core}'s {@code DataSource}.
 */
public final class DatabaseService implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseService.class);

    private static final String DRIVER_CLASS_NAME = "org.postgresql.Driver";
    private static final String VALIDATION_QUERY = "/* ping */ SELECT 1";

    private final DataSource dataSource;
    private final int maxRows;

    public DatabaseService() {
        String url = PropertyStore.getString("jdbc.url");
        if (url == null || url.isBlank()) {
            throw new IllegalStateException(
                "jdbc.url is not configured. Set VF_HOME to a directory containing "
                    + "config/properties with jdbc.url, jdbc.user and jdbc.password.");
        }
        this.maxRows = PropertyStore.getInt("vf.mcp.maxRows", 1000);

        DataSource ds = new DataSource();
        ds.setDriverClassName(DRIVER_CLASS_NAME);
        ds.setUrl(url);
        ds.setUsername(PropertyStore.getString("jdbc.user"));
        ds.setPassword(PropertyStore.decrypt("jdbc.password"));
        ds.setDefaultAutoCommit(true);
        ds.setInitialSize(1);
        ds.setMaxActive(8);
        ds.setMaxIdle(4);
        ds.setMinIdle(1);
        ds.setMaxWait(30_000);
        ds.setTestOnBorrow(true);
        ds.setValidationQuery(VALIDATION_QUERY);
        ds.setValidationInterval(3_000L);
        this.dataSource = ds;
    }

    /** Verify connectivity; throws if the database is unreachable or misconfigured. */
    public void validate() throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            rs.next();
        }
        logger.info("Database connection validated: {}", dataSource.getUrl());
    }

    /** Table names in the connected schema. */
    public List<String> listTables() throws SQLException {
        String sql = "SELECT table_name FROM information_schema.tables "
            + "WHERE table_schema = current_schema() AND table_type = 'BASE TABLE' ORDER BY table_name";
        List<String> tables = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    /** Column metadata for a table in the connected schema. */
    public List<Map<String, Object>> describeTable(String table) throws SQLException {
        String sql = "SELECT c.column_name, c.data_type, c.is_nullable, c.column_default, "
            + "CASE WHEN tc.constraint_type = 'PRIMARY KEY' THEN 'PRIMARY' "
            + "WHEN tc.constraint_type = 'UNIQUE' THEN 'UNIQUE' END AS column_key, "
            + "CASE WHEN c.is_identity = 'YES' THEN 'IDENTITY' ELSE '' END AS extra "
            + "FROM information_schema.columns c "
            + "LEFT JOIN information_schema.key_column_usage kcu "
            + "ON kcu.table_schema = c.table_schema AND kcu.table_name = c.table_name "
            + "AND kcu.column_name = c.column_name "
            + "LEFT JOIN information_schema.table_constraints tc "
            + "ON tc.constraint_schema = kcu.constraint_schema AND tc.constraint_name = kcu.constraint_name "
            + "WHERE c.table_schema = current_schema() AND c.table_name = ? ORDER BY c.ordinal_position";
        List<Map<String, Object>> columns = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> col = new LinkedHashMap<>(6);
                    col.put("name", rs.getString("column_name"));
                    col.put("type", rs.getString("data_type"));
                    col.put("nullable", "YES".equalsIgnoreCase(rs.getString("is_nullable")));
                    col.put("key", rs.getString("column_key"));
                    col.put("default", rs.getString("column_default"));
                    col.put("extra", rs.getString("extra"));
                    columns.add(col);
                }
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalArgumentException("Table not found or has no columns: " + table);
        }
        return columns;
    }

    /**
     * Run a SELECT-family query. Result is {@code {columns, rowCount, truncated, rows}}, where rows
     * are column-keyed maps. At most {@code vf.mcp.maxRows} rows are returned; if more exist,
     * {@code truncated} is {@code true}.
     */
    public Map<String, Object> read(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.setMaxRows(maxRows + 1); // one extra row to detect truncation
            try (ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();

                List<String> columns = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    columns.add(md.getColumnLabel(i));
                }

                List<Map<String, Object>> rows = new ArrayList<>();
                boolean truncated = false;
                while (rs.next()) {
                    if (rows.size() >= maxRows) {
                        truncated = true;
                        break;
                    }
                    Map<String, Object> row = new LinkedHashMap<>(colCount);
                    for (int i = 1; i <= colCount; i++) {
                        row.put(columns.get(i - 1), normalize(rs.getObject(i)));
                    }
                    rows.add(row);
                }

                Map<String, Object> result = new LinkedHashMap<>(4);
                result.put("columns", columns);
                result.put("rowCount", rows.size());
                result.put("truncated", truncated);
                result.put("rows", rows);
                return result;
            }
        }
    }

    /**
     * Execute a write statement (DML or DDL). Returns the affected-row count, or {@code 0} for
     * statements that have no update count (e.g. DDL).
     */
    public int write(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement()) {
            st.execute(sql);
            return Math.max(st.getUpdateCount(), 0);
        }
    }

    /** Convert JDBC values that JSON cannot render cleanly into stable string forms. */
    private static Object normalize(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        if (value instanceof java.sql.Timestamp || value instanceof java.sql.Date
            || value instanceof java.sql.Time || value instanceof Temporal) {
            return value.toString();
        }
        return value;
    }

    @Override
    public void close() {
        dataSource.close();
    }
}
