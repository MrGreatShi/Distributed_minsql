package org.example.minisql.client.router;

import org.example.minisql.client.cache.RouteCache;
import org.example.minisql.client.masterlink.MasterClient;
import org.example.minisql.protocol.command.client.ClientOperation;
import org.example.minisql.protocol.command.client.SqlRequest;
import org.example.minisql.protocol.command.master.RouteInfo;

import java.io.IOException;

/**
 * 客户端路由器。
 * <p>
 * 负责为每条 SQL 找到对应的 Region：
 * <ol>
 *   <li>CREATE TABLE 始终走 Master（必须让 Master 分配副本节点），结果写入缓存</li>
 *   <li>其他操作：先查本地缓存，缓存未命中才查 Master</li>
 * </ol>
 * <p>
 * 当 Region 连接失败时，上层应调用 {@code invalidate} 并重试路由。
 */
public final class QueryRouter {
    private final RouteCache cache;
    private final MasterClient masterClient;

    public QueryRouter(RouteCache cache, MasterClient masterClient) {
        this.cache = cache;
        this.masterClient = masterClient;
    }

    /**
     * 为请求解析并返回目标 Region 的路由信息。
     *
     * @throws IllegalArgumentException 若请求类型不需要路由（如 SHOW_TABLES）
     * @throws IOException              若 Master 返回错误或连接失败
     */
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

    /** 将指定表的缓存条目删除，下次请求时将重新查询 Master。 */
    public void invalidate(String tableName) {
        cache.invalidate(tableName);
    }

    /** 清除全部缓存条目。 */
    public void clear() {
        cache.clear();
    }

}
