package org.example.minisql.client.router;

import org.example.minisql.client.cache.RouteCache;
import org.example.minisql.client.masterlink.MasterClient;
import org.example.minisql.protocol.command.client.ClientOperation;
import org.example.minisql.protocol.command.client.SqlRequest;
import org.example.minisql.protocol.command.master.RouteInfo;

import java.io.IOException;

public final class QueryRouter {
    private final RouteCache cache;
    private final MasterClient masterClient;

    public QueryRouter(RouteCache cache, MasterClient masterClient) {
        this.cache = cache;
        this.masterClient = masterClient;
    }

    public RouteInfo route(SqlRequest request) throws IOException {
        if (!request.operation().needsRoute()) {
            throw new IllegalArgumentException("Request does not need route");
        }
        if (request.operation() == ClientOperation.CREATE_TABLE) {
            RouteInfo route = masterClient.queryRoute(request.operation().routeVerb(), request.tableName());
            cache.put(route);
            return route;
        }
        RouteInfo cached = cache.get(request.tableName()).orElse(null);
        if (cached != null) {
            return cached;
        }
        RouteInfo route = masterClient.queryRoute(request.operation().routeVerb(), request.tableName());
        cache.put(route);
        return route;
    }

    public void invalidate(String tableName) {
        cache.invalidate(tableName);
    }

    public void clear() {
        cache.clear();
    }

}
