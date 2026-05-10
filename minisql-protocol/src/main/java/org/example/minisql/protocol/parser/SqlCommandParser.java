package org.example.minisql.protocol.parser;

import org.example.minisql.protocol.command.client.ClientOperation;
import org.example.minisql.protocol.command.client.SqlRequest;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SqlCommandParser {
    private static final String IDENTIFIER = "([A-Za-z_][A-Za-z0-9_]*)";
    private static final Pattern CREATE_TABLE = Pattern.compile("^create\\s+table\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern CREATE_INDEX = Pattern.compile("^create\\s+index\\s+" + IDENTIFIER + "\\s+on\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_TABLE = Pattern.compile("^drop\\s+table\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DROP_INDEX_WITH_TABLE = Pattern.compile("^drop\\s+index\\s+" + IDENTIFIER + "\\s+on\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SELECT = Pattern.compile("^select\\b.*\\bfrom\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern INSERT = Pattern.compile("^insert\\s+into\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DELETE = Pattern.compile("^delete\\s+from\\s+" + IDENTIFIER + "\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private SqlCommandParser() {
    }

    public static SqlRequest parse(String sql) {
        String normalized = normalizeSql(sql);
        String body = stripTerminator(normalized).trim();
        String lowered = body.toLowerCase(Locale.ROOT);
        if ("show tables".equals(lowered)) {
            return new SqlRequest(ClientOperation.SHOW_TABLES, null, normalized);
        }
        Matcher matcher = CREATE_TABLE.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.CREATE_TABLE, matcher.group(1), normalized);
        }
        matcher = CREATE_INDEX.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.CREATE_INDEX, matcher.group(2), normalized);
        }
        matcher = DROP_TABLE.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.DROP_TABLE, matcher.group(1), normalized);
        }
        matcher = DROP_INDEX_WITH_TABLE.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.DROP_INDEX, matcher.group(2), normalized);
        }
        if (lowered.startsWith("drop index")) {
            throw new IllegalArgumentException("DROP INDEX must include ON <table> for distributed routing");
        }
        matcher = SELECT.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.SELECT, matcher.group(1), normalized);
        }
        matcher = INSERT.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.INSERT, matcher.group(1), normalized);
        }
        matcher = DELETE.matcher(body);
        if (matcher.matches()) {
            return new SqlRequest(ClientOperation.DELETE, matcher.group(1), normalized);
        }
        throw new IllegalArgumentException("Unsupported SQL command");
    }

    private static String normalizeSql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL is empty");
        }
        String singleLine = sql.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
        if (singleLine.endsWith(";;")) {
            return singleLine;
        }
        return singleLine + ";;";
    }

    private static String stripTerminator(String sql) {
        String stripped = sql.trim();
        while (stripped.endsWith(";")) {
            stripped = stripped.substring(0, stripped.length() - 1).trim();
        }
        return stripped;
    }
}
