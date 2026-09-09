# MCP Server (`mcp`)

A [Model Context Protocol](https://modelcontextprotocol.io) stdio server that gives an MCP client
read and write access to the local PostgreSQL database.

It depends only on `foundation`, pgJDBC, and the connection pool. PostgreSQL must already be running.

## Prerequisites

- Java 25 and PostgreSQL.
- `VF_HOME` pointing to a directory containing `config/properties/*.properties` with:
  - `jdbc.url` (for example, `jdbc:postgresql://localhost:5432/vf`)
  - `jdbc.user`
  - `jdbc.password` (plain or `ENC(...)`; set `VF_KEK` when encrypted)

## Build and run

```bash
mvn -pl mcp -am clean install
VF_HOME=/path/to/home java -jar mcp/target/mcp.jar
```

The build creates `mcp/target/mcp.jar`. Startup validates the connection and reports configuration
or connectivity failures on stderr; stdout is reserved for JSON-RPC.

Example client configuration:

```json
{
  "mcpServers": {
    "vf-postgresql": {
      "command": "java",
      "args": ["-jar", "/abs/path/to/mcp/target/mcp.jar"],
      "env": { "VF_HOME": "/path/to/home" }
    }
  }
}
```

## Tools

| Tool | Arguments | Description |
| --- | --- | --- |
| `list_tables` | - | Base tables in the current schema |
| `describe_table` | `table` | Column name, type, nullability, key, default, and identity metadata |
| `read_query` | `sql` | `SELECT`, `WITH`, or `EXPLAIN`; capped by `vf.mcp.maxRows` |
| `write_query` | `sql` | DML and DDL; returns affected rows |

`vf.mcp.maxRows` defaults to `1000` and can be overridden through `VF_HOME`.
