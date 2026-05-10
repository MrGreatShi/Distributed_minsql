package org.example.minisql.common.config;

public record MiniSqlConfig(
    String masterHost,
    String zookeeperRegistryPath,
    String zookeeperConnectString,
    int zookeeperSessionTimeoutMillis,
    int zookeeperConnectionTimeoutMillis,
    int masterPort,
    int regionClientPort,
    int regionMigrationPort,
    int socketTimeoutMillis
) {
}

