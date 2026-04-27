package org.example.minisql.master.bootstrap;

import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.common.config.MiniSqlConfigLoader;

public final class MasterServerApplication {
    private MasterServerApplication() {
    }

    public static void main(String[] args) {
        MiniSqlConfig config = MiniSqlConfigLoader.load();
        System.out.println("MiniSQL Master skeleton started.");
        System.out.printf("zookeeper=%s, masterPort=%d%n",
            config.zookeeperRegistryPath(),
            config.masterPort());
    }
}

