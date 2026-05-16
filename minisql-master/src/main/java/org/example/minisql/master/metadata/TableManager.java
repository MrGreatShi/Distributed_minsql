package org.example.minisql.master.metadata;

import org.example.minisql.protocol.command.master.RouteInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Master 内存元数据中心。
 * <p>
 * 维护三个内存结构（均不持久化到磁盘）：
 * <ul>
 *   <li>{@code tableRoutes}  — 表名 → RouteInfo（主Region + 副Region）</li>
 *   <li>{@code regionTables} — RegionIP → 该 Region 所持有的表名集合（负载表）</li>
 *   <li>{@code healthyRegions} — 当前存活的 Region 集合</li>
 *   <li>{@code knownRegions}  — 迎今见过的全部 Region（用于判断是新节点还是恢复节点）</li>
 * </ul>
 * 全部公共方法均为 {@code synchronized}，线程安全。
 */
public final class TableManager {
    private final Map<String, RouteInfo> tableRoutes = new HashMap<>();
    private final Map<String, Set<String>> regionTables = new HashMap<>();
    private final Set<String> healthyRegions = new HashSet<>();
    private final Set<String> knownRegions = new HashSet<>();

    /** 将 Region 标记为健康并加入已知节点集合。 */
    public synchronized void markRegionAvailable(String region) {
        String normalized = normalizeRegion(region);
        knownRegions.add(normalized);
        healthyRegions.add(normalized);
        regionTables.computeIfAbsent(normalized, ignored -> new HashSet<>());
    }

    /**
     * 将 Region 标记为不健康，并为该 Region 上的每张表制定剗除计划。
     * <p>
     * 对每张受影响的表：
     * <ol>
     *   <li>找到另一副本节点（幸存者）</li>
     *   <li>选负载最小的健康节点作为新副本</li>
     *   <li>更新内存路由表，生成 CopyTask（让幸存者将数据推送给新副本）</li>
     * </ol>
     *
     * @return 需要执行的数据备份任务列表
     */
    public synchronized List<CopyTask> markRegionUnavailableAndPlanRecovery(String region) {
        String lostRegion = normalizeRegion(region);
        healthyRegions.remove(lostRegion);
        List<RouteInfo> affectedRoutes = tableRoutes.values().stream()
            .filter(route -> containsRegion(route, lostRegion))
            .toList();
        List<CopyTask> tasks = new ArrayList<>();
        for (RouteInfo route : affectedRoutes) {
            String survivor = otherReplica(route, lostRegion).orElse(null);
            removeTableFromRegion(lostRegion, route.tableName());
            if (survivor == null) {
                tableRoutes.remove(route.tableName());
                continue;
            }
            Optional<String> replacement = chooseLeastLoadedRegion(Set.of(lostRegion, survivor));
            if (replacement.isEmpty()) {
                tableRoutes.put(route.tableName(), new RouteInfo(route.tableName(), survivor, null));
                continue;
            }
            String target = replacement.get();
            tableRoutes.put(route.tableName(), new RouteInfo(route.tableName(), survivor, target));
            addTableToRegion(target, route.tableName());
            tasks.add(new CopyTask(survivor, target, route.tableName()));
        }
        regionTables.remove(lostRegion);
        return tasks;
    }

    /**
     * 确保表已建立路由条目。
     * 若路由已存在则直接返回（幂等）；否则列中选最少负载的两个 Region。
     *
     * @throws IllegalStateException 当前健康 Region 数量 &lt; 2
     */
    public synchronized RouteInfo ensureRouteForCreate(String tableName) {
        String normalized = normalizeTable(tableName);
        RouteInfo existing = tableRoutes.get(normalized);
        if (existing != null) {
            return existing;
        }
        List<String> selected = chooseLeastLoadedRegions(2, Set.of());
        if (selected.size() < 2) {
            throw new IllegalStateException("At least two healthy regions are required to create table " + normalized);
        }
        RouteInfo route = new RouteInfo(normalized, selected.get(0), selected.get(1));
        tableRoutes.put(normalized, route);
        addTableToRegion(selected.get(0), normalized);
        addTableToRegion(selected.get(1), normalized);
        return route;
    }

    public synchronized Optional<RouteInfo> routeOf(String tableName) {
        return Optional.ofNullable(tableRoutes.get(normalizeTable(tableName)));
    }

    public synchronized List<String> tableNames() {
        return new ArrayList<>(new TreeSet<>(tableRoutes.keySet()));
    }

    /**
     * Region 启动/重连时上报已持有的表列表。
     * 将 Region 标记为健康，并將这些表一并注册到内存路由表。
     */
    public synchronized void registerRecoveredTables(String region, Collection<String> tables) {
        String normalizedRegion = normalizeRegion(region);
        markRegionAvailable(normalizedRegion);
        for (String table : tables) {
            if (table != null && !table.isBlank()) {
                registerTableOnRegion(normalizedRegion, normalizeTable(table));
            }
        }
    }

    public synchronized void registerTableOnRegion(String region, String tableName) {
        String normalizedRegion = normalizeRegion(region);
        String normalizedTable = normalizeTable(tableName);
        markRegionAvailable(normalizedRegion);
        RouteInfo route = tableRoutes.get(normalizedTable);
        if (route == null) {
            tableRoutes.put(normalizedTable, new RouteInfo(normalizedTable, normalizedRegion, null));
            addTableToRegion(normalizedRegion, normalizedTable);
            return;
        }
        if (!containsRegion(route, normalizedRegion) && !route.hasSecondaryRegion()) {
            tableRoutes.put(normalizedTable, new RouteInfo(normalizedTable, route.primaryRegion(), normalizedRegion));
            addTableToRegion(normalizedRegion, normalizedTable);
        }
    }

    /** 将表从全局路由表中删除（不属于任何 Region ）。 */
    public synchronized void dropTable(String tableName) {
        String normalized = normalizeTable(tableName);
        RouteInfo removed = tableRoutes.remove(normalized);
        if (removed == null) {
            return;
        }
        removeTableFromRegion(removed.primaryRegion(), normalized);
        if (removed.hasSecondaryRegion()) {
            removeTableFromRegion(removed.secondaryRegion(), normalized);
        }
    }

    public synchronized List<String> healthyRegions() {
        return new ArrayList<>(new TreeSet<>(healthyRegions));
    }

    public synchronized List<String> knownRegions() {
        return new ArrayList<>(new TreeSet<>(knownRegions));
    }

    public synchronized boolean isKnownRegion(String region) {
        return knownRegions.contains(normalizeRegion(region));
    }

    /**
     * 从健康 Region 中选出负载最小的若干个（负载 = 该 Region 上的表数量）。
     * 负载相同时按 IP 字典序排序，保证选择确定性。
     */
    private List<String> chooseLeastLoadedRegions(int count, Set<String> excluded) {
        return healthyRegions.stream()
            .filter(region -> !excluded.contains(region))
            .sorted(Comparator.comparingInt(this::loadOf).thenComparing(Comparator.naturalOrder()))
            .limit(count)
            .toList();
    }

    private Optional<String> chooseLeastLoadedRegion(Set<String> excluded) {
        List<String> selected = chooseLeastLoadedRegions(1, excluded);
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(selected.get(0));
    }

    private int loadOf(String region) {
        return regionTables.getOrDefault(region, Set.of()).size();
    }

    private void addTableToRegion(String region, String tableName) {
        regionTables.computeIfAbsent(region, ignored -> new HashSet<>()).add(tableName);
    }

    private void removeTableFromRegion(String region, String tableName) {
        Set<String> tables = regionTables.get(region);
        if (tables != null) {
            tables.remove(tableName);
        }
    }

    private boolean containsRegion(RouteInfo route, String region) {
        return route.primaryRegion().equals(region) || (route.hasSecondaryRegion() && route.secondaryRegion().equals(region));
    }

    private Optional<String> otherReplica(RouteInfo route, String region) {
        if (route.primaryRegion().equals(region) && route.hasSecondaryRegion()) {
            return Optional.of(route.secondaryRegion());
        }
        if (route.hasSecondaryRegion() && route.secondaryRegion().equals(region)) {
            return Optional.of(route.primaryRegion());
        }
        return Optional.empty();
    }

    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) {
            throw new IllegalArgumentException("Region is empty");
        }
        return region.trim();
    }

    private String normalizeTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("Table name is empty");
        }
        return tableName.trim().toLowerCase();
    }
}
