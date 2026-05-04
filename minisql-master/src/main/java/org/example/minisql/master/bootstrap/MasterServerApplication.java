package org.example.minisql.master.bootstrap;

import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.common.config.MiniSqlConfigLoader;
import org.example.minisql.master.handler.client.ClientCommandHandler;
import org.example.minisql.master.handler.region.RegionCommandHandler;
import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.master.socket.MasterSocketServer;
import org.example.minisql.master.strategy.RegionCommandSender;
import org.example.minisql.master.strategy.RegionLifecycleService;
import org.example.minisql.master.zk.ZookeeperRegionRegistry;

import java.util.concurrent.CountDownLatch;

public final class MasterServerApplication {
    private MasterServerApplication() {
    }

    public static void main(String[] args) throws Exception {
        MiniSqlConfig config = MiniSqlConfigLoader.load();
        TableManager tableManager = new TableManager();
        RegionCommandSender commandSender = new RegionCommandSender(config);
        RegionLifecycleService lifecycleService = new RegionLifecycleService(tableManager, commandSender);
        ClientCommandHandler clientHandler = new ClientCommandHandler(tableManager);
        RegionCommandHandler regionHandler = new RegionCommandHandler(tableManager);
        ZookeeperRegionRegistry registry = new ZookeeperRegionRegistry(config, lifecycleService);
        MasterSocketServer socketServer = new MasterSocketServer(config, clientHandler, regionHandler);
        socketServer.start();
        try {
            registry.start();
        } catch (Exception e) {
            System.err.printf("ZooKeeper registry is unavailable: %s%n", e.getMessage());
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            socketServer.close();
            registry.close();
            lifecycleService.close();
        }));
        System.out.println("MiniSQL Master started.");
        System.out.printf("zookeeper=%s%s, masterPort=%d%n",
            config.zookeeperConnectString(),
            config.zookeeperRegistryPath(),
            config.masterPort());
        new CountDownLatch(1).await();
    }
}

