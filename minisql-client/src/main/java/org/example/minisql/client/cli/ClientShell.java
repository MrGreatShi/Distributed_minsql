package org.example.minisql.client.cli;

import org.example.minisql.client.masterlink.MasterClient;
import org.example.minisql.client.regionlink.RegionClient;
import org.example.minisql.client.router.QueryRouter;
import org.example.minisql.protocol.command.client.SqlRequest;
import org.example.minisql.protocol.command.master.RouteInfo;
import org.example.minisql.protocol.parser.SqlCommandParser;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

/**
 * 命令行交互入口。
 * <p>
 * 主循环读取用户输入，支持多行 SQL（以 {@code ;;} 结尾）。
 * <p>
 * 执行流程：
 * <ol>
 *   <li>SHOW TABLES — 直接查 Master，打印表名列表</li>
 *   <li>其他 SQL — 解析表名→查路由→发往 Region 执行</li>
 *   <li>写操作（INSERT/DELETE/CREATE/DROP TABLE）— 主副 Region 双写</li>
 *   <li>读操作（SELECT）— 仅发主 Region</li>
 * </ol>
 */
public final class ClientShell {
    private final MasterClient masterClient;
    private final RegionClient regionClient;
    private final QueryRouter router;

    public ClientShell(MasterClient masterClient, RegionClient regionClient, QueryRouter router) {
        this.masterClient = masterClient;
        this.regionClient = regionClient;
        this.router = router;
    }

    public void run() {
        System.out.println("MiniSQL Client started. Type SQL ending with ;;, or exit/quit to leave.");
        Scanner scanner = new Scanner(System.in);
        StringBuilder buffer = new StringBuilder();
        while (true) {
            System.out.print(buffer.isEmpty() ? "minisql> " : "      -> ");
            if (!scanner.hasNextLine()) {
                return;
            }
            String line = scanner.nextLine().trim();
            if (buffer.isEmpty() && isExitCommand(line)) {
                return;
            }
            if (line.isEmpty()) {
                continue;
            }
            if (!buffer.isEmpty()) {
                buffer.append(' ');
            }
            buffer.append(line);
            if (!line.endsWith(";;")) {
                continue;
            }
            execute(buffer.toString());
            buffer.setLength(0);
        }
    }

    private void execute(String sql) {
        try {
            SqlRequest request = SqlCommandParser.parse(sql);
            // SHOW TABLES 无需路由，直接查 Master
            if (!request.operation().needsRoute()) {
                printTables(masterClient.showTables());
                return;
            }
            RouteInfo route = router.route(request);
            String result = executeByRoute(request, route);
            System.out.println(result);
            // DROP TABLE 成功后主动将路由缓存清除，避免旧缓存指向已删除的表
            if (request.operation().invalidatesCacheAfterSuccess()) {
                router.invalidate(request.tableName());
            }
        } catch (RuntimeException | IOException e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    /**
     * 根据路由信息执行 SQL。
     * <ul>
     *   <li>读操作（SELECT）— 仅发主 Region</li>
     *   <li>写操作— 主 Region 必须成功，副 Region 失败仅记录警告（不中断流程）</li>
     * </ul>
     * <p>
     * 若主 Region 第一次失败，会自动删除缓存并重新查路由后重试一次。
     */
    private String executeByRoute(SqlRequest request, RouteInfo route) throws IOException {
        if (!request.operation().writesData()) {
            return executeRead(request, route.primaryRegion());
        }
        RouteInfo activeRoute = route;
        String primaryResult;
        try {
            primaryResult = regionClient.execute(activeRoute.primaryRegion(), request.sql());
        } catch (IOException e) {
            // 主 Region 失败：删除缓存并重新向 Master 获取最新路由，再次尝试
            router.invalidate(request.tableName());
            activeRoute = router.route(request);
            primaryResult = regionClient.execute(activeRoute.primaryRegion(), request.sql());
        }
        if (activeRoute.hasSecondaryRegion()) {
            try {
                regionClient.execute(activeRoute.secondaryRegion(), request.sql());
            } catch (IOException e) {
                System.err.printf("WARN: secondary write failed on %s: %s%n",
                    activeRoute.secondaryRegion(), e.getMessage());
            }
        }
        return primaryResult;
    }

    private String executeRead(SqlRequest request, String region) throws IOException {
        try {
            return regionClient.execute(region, request.sql());
        } catch (IOException first) {
            router.invalidate(request.tableName());
            RouteInfo refreshed = router.route(request);
            try {
                return regionClient.execute(refreshed.primaryRegion(), request.sql());
            } catch (IOException second) {
                second.addSuppressed(first);
                throw second;
            }
        }
    }

    private void printTables(List<String> tables) {
        if (tables.isEmpty()) {
            System.out.println("No tables.");
            return;
        }
        for (String table : tables) {
            System.out.println(table);
        }
    }

    private boolean isExitCommand(String line) {
        return "exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line);
    }
}
