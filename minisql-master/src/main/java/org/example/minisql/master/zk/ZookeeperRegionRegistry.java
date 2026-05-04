package org.example.minisql.master.zk;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.cache.ChildData;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.master.strategy.RegionLifecycleService;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public final class ZookeeperRegionRegistry implements AutoCloseable {
    private final MiniSqlConfig config;
    private final RegionLifecycleService lifecycleService;
    private final Map<String, String> pathToRegion = new ConcurrentHashMap<>();
    private CuratorFramework client;
    private PathChildrenCache cache;

    public ZookeeperRegionRegistry(MiniSqlConfig config, RegionLifecycleService lifecycleService) {
        this.config = config;
        this.lifecycleService = lifecycleService;
    }

    public void start() throws Exception {
        client = CuratorFrameworkFactory.newClient(
            config.zookeeperConnectString(),
            config.zookeeperSessionTimeoutMillis(),
            config.zookeeperConnectionTimeoutMillis(),
            new ExponentialBackoffRetry(1000, 3)
        );
        client.start();
        if (!client.blockUntilConnected(config.zookeeperConnectionTimeoutMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("Failed to connect ZooKeeper: " + config.zookeeperConnectString());
        }
        ensureRegistryPath();
        cache = new PathChildrenCache(client, config.zookeeperRegistryPath(), true);
        cache.getListenable().addListener((ignoredClient, event) -> {
            ChildData data = event.getData();
            if (data == null) {
                return;
            }
            switch (event.getType()) {
                case CHILD_ADDED, CHILD_UPDATED -> handleRegionAvailable(data);
                case CHILD_REMOVED -> handleRegionRemoved(data);
                default -> {
                }
            }
        });
        cache.start(PathChildrenCache.StartMode.BUILD_INITIAL_CACHE);
        for (ChildData data : cache.getCurrentData()) {
            handleRegionAvailable(data);
        }
    }

    private void ensureRegistryPath() throws Exception {
        if (client.checkExists().forPath(config.zookeeperRegistryPath()) == null) {
            client.create().creatingParentsIfNeeded().forPath(config.zookeeperRegistryPath());
        }
    }

    private void handleRegionAvailable(ChildData data) {
        String region = extractRegion(data);
        pathToRegion.put(data.getPath(), region);
        lifecycleService.onRegionAvailable(region);
    }

    private void handleRegionRemoved(ChildData data) {
        String region = pathToRegion.remove(data.getPath());
        if (region == null || region.isBlank()) {
            region = extractRegion(data);
        }
        lifecycleService.onRegionLost(region);
    }

    private String extractRegion(ChildData data) {
        byte[] payload = data.getData();
        if (payload != null && payload.length > 0) {
            String value = new String(payload, StandardCharsets.UTF_8).trim();
            if (!value.isBlank()) {
                return value;
            }
        }
        String path = data.getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < path.length()) {
            return path.substring(slash + 1);
        }
        return path;
    }

    @Override
    public void close() {
        if (cache != null) {
            try {
                cache.close();
            } catch (Exception ignored) {
            }
        }
        if (client != null) {
            client.close();
        }
    }
}
