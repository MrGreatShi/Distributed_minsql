package org.example.minisql.master.handler.region;

import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RegionCommandHandler 单元测试。
 * 覆盖：recover（含表列表/空列表）、create（正常/无参数）、drop、非法来源。
 */
class RegionCommandHandlerTest {
    private static final String REGION_A = "10.0.0.1";
    private static final String REGION_B = "10.0.0.2";

    private TableManager tableManager;
    private RegionCommandHandler handler;

    @BeforeEach
    void setUp() {
        tableManager = new TableManager();
        handler = new RegionCommandHandler(tableManager);
    }

    // ---- RECOVER ----

    @Test
    void recoverWithTableListRegistersTablesAndRegion() {
        String resp = handler.handle(
            cmd(CommandSource.REGION, CommandVerb.RECOVER, List.of("users", "orders")),
            REGION_A
        );
        assertOk(resp);
        assertTrue(tableManager.isKnownRegion(REGION_A));
        assertTrue(tableManager.routeOf("users").isPresent());
        assertTrue(tableManager.routeOf("orders").isPresent());
    }

    @Test
    void recoverWithNoTablesStillMarksRegionHealthy() {
        String resp = handler.handle(
            cmd(CommandSource.REGION, CommandVerb.RECOVER, List.of()),
            REGION_A
        );
        assertOk(resp);
        assertTrue(tableManager.isKnownRegion(REGION_A));
    }

    // ---- CREATE ----

    @Test
    void createNotifiesTableAddedToRegion() {
        handler.handle(cmd(CommandSource.REGION, CommandVerb.RECOVER, List.of()), REGION_A);

        String resp = handler.handle(
            cmd(CommandSource.REGION, CommandVerb.CREATE, List.of("products")),
            REGION_A
        );
        assertOk(resp);
        Optional<org.example.minisql.protocol.command.master.RouteInfo> route = tableManager.routeOf("products");
        assertTrue(route.isPresent());
    }

    @Test
    void createWithNoArgsReturnsError() {
        String resp = handler.handle(
            cmd(CommandSource.REGION, CommandVerb.CREATE, List.of()),
            REGION_A
        );
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    // ---- DROP ----

    @Test
    void dropRemovesTableFromRoutes() {
        // 先由两个 Region 分别上报同一张表，建立路由
        handler.handle(cmd(CommandSource.REGION, CommandVerb.RECOVER, List.of("logs")), REGION_A);
        handler.handle(cmd(CommandSource.REGION, CommandVerb.RECOVER, List.of("logs")), REGION_B);

        assertTrue(tableManager.routeOf("logs").isPresent());

        String resp = handler.handle(
            cmd(CommandSource.REGION, CommandVerb.DROP, List.of("logs")),
            REGION_A
        );
        assertOk(resp);
        assertFalse(tableManager.routeOf("logs").isPresent());
    }

    @Test
    void dropWithNoArgsReturnsError() {
        String resp = handler.handle(
            cmd(CommandSource.REGION, CommandVerb.DROP, List.of()),
            REGION_A
        );
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    // ---- 非法来源 ----

    @Test
    void rejectsNonRegionSource() {
        String resp = handler.handle(
            cmd(CommandSource.CLIENT, CommandVerb.RECOVER, List.of()),
            REGION_A
        );
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    // ---- helpers ----

    private static TextCommand cmd(CommandSource source, CommandVerb verb, List<String> args) {
        return LineProtocol.parse(LineProtocol.encode(source, verb, args));
    }

    private static void assertOk(String resp) {
        TextCommand parsed = LineProtocol.parse(resp);
        assertFalse(LineProtocol.isError(parsed), "Expected ok but got: " + resp);
    }
}
