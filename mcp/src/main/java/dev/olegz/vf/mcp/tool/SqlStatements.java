package dev.olegz.vf.mcp.tool;

import java.util.Locale;
import java.util.Set;

/**
 * SQL statement classification used to route a statement to {@code read_query} vs {@code write_query}.
 * Only the leading keyword is inspected, after stripping leading whitespace and SQL comments.
 */
public final class SqlStatements {
    private SqlStatements() {
    }

    /** Statement kinds accepted by {@code read_query}. Everything else is treated as a write. */
    private static final Set<String> READ_KEYWORDS =
        Set.of("SELECT", "WITH", "EXPLAIN");

    public static boolean isRead(String sql) {
        return READ_KEYWORDS.contains(firstKeyword(sql));
    }

    /**
     * The first SQL keyword (upper-cased), after stripping leading whitespace and comments
     * ({@code -- ...} and {@code /* ... *}{@code /}). Empty string if there is none.
     */
    public static String firstKeyword(String sql) {
        if (sql == null) {
            return "";
        }
        String s = stripLeading(sql);
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (Character.isLetter(c) || c == '_') {
                i++;
            } else {
                break;
            }
        }
        return s.substring(0, i).toUpperCase(Locale.ROOT);
    }

    private static String stripLeading(String sql) {
        int i = 0;
        int n = sql.length();
        while (i < n) {
            char c = sql.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '-' && i + 1 < n && sql.charAt(i + 1) == '-') {
                i += 2;
                while (i < n && sql.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && sql.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(sql.charAt(i) == '*' && sql.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, n); // skip the closing */ (clamped if unterminated)
            } else {
                break;
            }
        }
        return i >= n ? "" : sql.substring(i);
    }
}
