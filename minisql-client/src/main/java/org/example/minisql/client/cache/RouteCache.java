package org.example.minisql.client.cache;

import org.example.minisql.protocol.command.master.RouteInfo;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端本地路由缓存。
 * <p>
 * 缓存「表名 → RouteInfo(主Region IP + 副Region IP)」的映射，
 * 避免每条 SQL 都向 Master 发起路由查询，降低延迟。
 * <p>
 * 表名统一小写存储，查询时不区分大小写。
 * 在以下情况下需要主动失效（invalidate）：
 * <ul>
 *   <li>DROP TABLE 之后（表已被删除，路由无效）</li>
 *   <li>Region 连接失败（路由可能过期，需重查 Master）</li>
 * </ul>
 */
public final class RouteCache {
    private final Map<String, RouteInfo> routes = new ConcurrentHashMap<>();

    /** 根据表名查询缓存的路由信息；若缓存未命中，返回 empty。 */
    public Optional<RouteInfo> get(String tableName) {
        return Optional.ofNullable(routes.get(normalize(tableName)));
    }

    /** 将 Master 返回的路由结果存入缓存。 */
    public void put(RouteInfo route) {
        routes.put(normalize(route.tableName()), route);
    }

    /** 使指定表的缓存条目失效（表被删除或路由过期时调用）。 */
    public void invalidate(String tableName) {
        if (tableName != null && !tableName.isBlank()) {
            routes.remove(normalize(tableName));
        }
    }

    /** 清除全部缓存（目前未使用，保留供完整性测试）。 */
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
