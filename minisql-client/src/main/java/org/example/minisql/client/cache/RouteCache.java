package org.example.minisql.client.cache;

import org.example.minisql.protocol.command.master.RouteInfo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class RouteCache {
    private final Map<String, RouteInfo> routes = new ConcurrentHashMap<>();

    public Optional<RouteInfo> get(String tableName) {
        return Optional.ofNullable(routes.get(normalize(tableName)));
    }

    public void put(RouteInfo route) {
        routes.put(normalize(route.tableName()), route);
    }

    public void invalidate(String tableName) {
        if (tableName != null && !tableName.isBlank()) {
            routes.remove(normalize(tableName));
        }
    }

    public void clear() {
        routes.clear();
    }

    private String normalize(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is empty");
        }
        return tableName.trim().toLowerCase();
    }
}
