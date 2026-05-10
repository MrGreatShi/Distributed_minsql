package org.example.minisql.client.cli;

import org.example.minisql.client.cache.RouteCache;
import org.example.minisql.client.masterlink.MasterClient;
import org.example.minisql.client.regionlink.RegionClient;
import org.example.minisql.client.router.QueryRouter;
import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.common.config.MiniSqlConfigLoader;

public final class ClientApplication {
    private ClientApplication() {
    }

    public static void main(String[] args) {
        MiniSqlConfig config = MiniSqlConfigLoader.load();
        RouteCache cache = new RouteCache();
        MasterClient masterClient = new MasterClient(config);
        RegionClient regionClient = new RegionClient(config);
        QueryRouter router = new QueryRouter(cache, masterClient);
        new ClientShell(masterClient, regionClient, router).run();
    }
}

