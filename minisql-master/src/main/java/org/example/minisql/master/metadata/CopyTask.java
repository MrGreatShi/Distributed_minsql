package org.example.minisql.master.metadata;

public record CopyTask(
    String sourceRegion,
    String targetRegion,
    String tableName
) {
}
