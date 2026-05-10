package org.example.minisql.protocol.command.client;

public record SqlRequest(
    ClientOperation operation,
    String tableName,
    String sql
) {
}
