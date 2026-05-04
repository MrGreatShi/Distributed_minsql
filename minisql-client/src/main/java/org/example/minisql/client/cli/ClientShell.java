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
            if (!request.operation().needsRoute()) {
                printTables(masterClient.showTables());
                return;
            }
            RouteInfo route = router.route(request);
            String result = executeByRoute(request, route);
            System.out.println(result);
            if (request.operation().invalidatesCacheAfterSuccess()) {
                router.invalidate(request.tableName());
            }
        } catch (RuntimeException | IOException e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }

    private String executeByRoute(SqlRequest request, RouteInfo route) throws IOException {
        if (!request.operation().writesData()) {
            return executeRead(request, route.primaryRegion());
        }
        RouteInfo activeRoute = route;
        String primaryResult;
        try {
            primaryResult = regionClient.execute(activeRoute.primaryRegion(), request.sql());
        } catch (IOException e) {
            router.invalidate(request.tableName());
            activeRoute = router.route(request);
            primaryResult = regionClient.execute(activeRoute.primaryRegion(), request.sql());
        }
        if (!activeRoute.hasSecondaryRegion()) {
            return primaryResult;
        }
        try {
            String secondaryResult = regionClient.execute(activeRoute.secondaryRegion(), request.sql());
            return primaryResult + System.lineSeparator() + secondaryResult;
        } catch (IOException e) {
            router.invalidate(request.tableName());
            return primaryResult + System.lineSeparator() + "WARN: secondary write failed on " + activeRoute.secondaryRegion() + ": " + e.getMessage();
        }
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
