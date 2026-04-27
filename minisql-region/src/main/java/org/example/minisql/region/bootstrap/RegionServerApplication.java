package org.example.minisql.region.bootstrap;

import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.common.config.MiniSqlConfigLoader;

public final class RegionServerApplication {
    private RegionServerApplication() {
    }

    public static void main(String[] args) {
        MiniSqlConfig config = MiniSqlConfigLoader.load();
        System.out.println("MiniSQL Region skeleton started.");
        System.out.printf("zookeeper=%s, clientPort=%d, migrationPort=%d%n",
            config.zookeeperRegistryPath(),
            config.regionClientPort(),
            config.regionMigrationPort());
    }
}

