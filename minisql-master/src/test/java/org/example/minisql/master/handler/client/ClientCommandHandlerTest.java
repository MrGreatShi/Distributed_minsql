package org.example.minisql.master.handler.client;

import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ClientCommandHandler 单元测试。
 * 覆盖：show tables、create（正常/无 Region）、路由查询（正常/表不存在）、非法来源命令。
 */
class ClientCommandHandlerTest {
    private TableManager tableManager;
    private ClientCommandHandler handler;

    @BeforeEach
    void setUp() {
        tableManager = new TableManager();
        handler = new ClientCommandHandler(tableManager);
    }

    // ---- SHOW TABLES ----

    @Test
    void showTablesEmpty() {
        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.SHOW, List.of("tables")));
        TextCommand parsed = LineProtocol.parse(resp);
        assertEquals(CommandVerb.SHOW, parsed.verb());
        assertTrue(parsed.arguments().isEmpty());
    }

    @Test
    void showTablesWithRoutes() {
        tableManager.markRegionAvailable("10.0.0.1");
        tableManager.markRegionAvailable("10.0.0.2");
        tableManager.ensureRouteForCreate("orders");

        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.SHOW, List.of("tables")));
        TextCommand parsed = LineProtocol.parse(resp);
        assertEquals(List.of("orders"), parsed.arguments());
    }

    // ---- CREATE ----

    @Test
    void createReturnsTwoRegionRoute() {
        tableManager.markRegionAvailable("10.0.0.1");
        tableManager.markRegionAvailable("10.0.0.2");

        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.CREATE, List.of("users")));
        TextCommand parsed = LineProtocol.parse(resp);
        assertFalse(LineProtocol.isError(parsed));
        assertEquals(CommandVerb.CREATE, parsed.verb());
        // args: <primary> <secondary> <table>
        assertEquals(3, parsed.arguments().size());
        assertEquals("users", parsed.argument(2));
    }

    @Test
    void createFailsWhenFewerThanTwoRegions() {
        tableManager.markRegionAvailable("10.0.0.1");

        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.CREATE, List.of("users")));
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    @Test
    void createWithNoArgsReturnsError() {
        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.CREATE, List.of()));
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    // ---- SELECT / INSERT / DELETE / DROP ----

    @Test
    void selectExistingTableReturnsRoute() {
        setupTwoRegionTable("inventory");

        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.SELECT, List.of("inventory")));
        TextCommand parsed = LineProtocol.parse(resp);
        assertFalse(LineProtocol.isError(parsed));
        assertEquals(CommandVerb.SELECT, parsed.verb());
    }

    @Test
    void selectNonExistentTableReturnsError() {
        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.SELECT, List.of("ghost")));
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
        assertTrue(LineProtocol.errorMessage(LineProtocol.parse(resp)).contains("ghost"));
    }

    @Test
    void dropExistingTableReturnsRoute() {
        setupTwoRegionTable("temp");

        String resp = handler.handle(cmd(CommandSource.CLIENT, CommandVerb.DROP, List.of("temp")));
        assertFalse(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    // ---- 非法来源 ----

    @Test
    void rejectsNonClientSource() {
        String resp = handler.handle(cmd(CommandSource.REGION, CommandVerb.SHOW, List.of("tables")));
        assertTrue(LineProtocol.isError(LineProtocol.parse(resp)));
    }

    // ---- helpers ----

    private void setupTwoRegionTable(String table) {
        tableManager.markRegionAvailable("10.0.0.1");
        tableManager.markRegionAvailable("10.0.0.2");
        tableManager.ensureRouteForCreate(table);
    }

    private static TextCommand cmd(CommandSource source, CommandVerb verb, List<String> args) {
        String raw = LineProtocol.encode(source, verb, args);
        return LineProtocol.parse(raw);
    }
}
