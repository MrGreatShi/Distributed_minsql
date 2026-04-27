package org.example.minisql.common.config;

public record MiniSqlConfig(
    String zookeeperRegistryPath,
    int masterPort,
    int regionClientPort,
    int regionMigrationPort
) {
}

