package dev.olegz.vf.mcp.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SqlStatementsTest {

    @Test
    void firstKeywordBasic() {
        assertEquals("SELECT", SqlStatements.firstKeyword("SELECT 1"));
        assertEquals("SELECT", SqlStatements.firstKeyword("select * from t"));
        assertEquals("INSERT", SqlStatements.firstKeyword("INSERT INTO t VALUES (1)"));
    }

    @Test
    void firstKeywordSkipsLeadingWhitespace() {
        assertEquals("SELECT", SqlStatements.firstKeyword("   \n\t SELECT 1"));
    }

    @Test
    void firstKeywordSkipsComments() {
        assertEquals("SELECT", SqlStatements.firstKeyword("-- a comment\nSELECT 1"));
        assertEquals("INSERT", SqlStatements.firstKeyword("/* block */ INSERT INTO t VALUES (1)"));
        assertEquals("UPDATE", SqlStatements.firstKeyword("/* multi\nline */\n  UPDATE t SET x=1"));
    }

    @Test
    void firstKeywordEmptyCases() {
        assertEquals("", SqlStatements.firstKeyword(null));
        assertEquals("", SqlStatements.firstKeyword("   "));
        assertEquals("", SqlStatements.firstKeyword("-- only a comment"));
        assertEquals("", SqlStatements.firstKeyword("/* unterminated"));
    }

    @Test
    void isReadTrueForReadStatements() {
        assertTrue(SqlStatements.isRead("SELECT 1"));
        assertTrue(SqlStatements.isRead("  with cte as (select 1) select * from cte"));
        assertTrue(SqlStatements.isRead("EXPLAIN SELECT 1"));
    }

    @Test
    void isReadFalseForWriteStatements() {
        assertFalse(SqlStatements.isRead("INSERT INTO t VALUES (1)"));
        assertFalse(SqlStatements.isRead("UPDATE t SET x = 1"));
        assertFalse(SqlStatements.isRead("DELETE FROM t"));
        assertFalse(SqlStatements.isRead("CREATE TABLE t (id INT)"));
        assertFalse(SqlStatements.isRead("ALTER TABLE t ADD COLUMN c INT"));
        assertFalse(SqlStatements.isRead("DROP TABLE t"));
        assertFalse(SqlStatements.isRead("TRUNCATE TABLE t"));
        assertFalse(SqlStatements.isRead(""));
    }
}
