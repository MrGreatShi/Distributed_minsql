package org.example.minisql.protocol.codec;

import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.example.minisql.protocol.command.master.RouteInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LineProtocol 编解码单元测试。
 * 覆盖：路由请求编码、路由响应编/解码、show tables、error/ok、master 管控命令、解析异常。
 */
class LineProtocolTest {

    // ---- 编码 / 解析基础 ----

    @Test
    void encodeAndParseClientRouteRequest() {
        String line = LineProtocol.encodeClientRouteRequest(CommandVerb.CREATE, "users");
        TextCommand cmd = LineProtocol.parse(line);
        assertEquals(CommandSource.CLIENT, cmd.source());
        assertEquals(CommandVerb.CREATE, cmd.verb());
        assertEquals(List.of("users"), cmd.arguments());
    }

    @Test
    void encodeShowTablesRequest() {
        String line = LineProtocol.encodeClientRouteRequest(CommandVerb.SHOW, null);
        TextCommand cmd = LineProtocol.parse(line);
        assertEquals(CommandVerb.SHOW, cmd.verb());
        assertEquals(List.of("tables"), cmd.arguments());
    }

    // ---- 路由响应 ----

    @Test
    void masterRouteResponseRoundTrip() {
        RouteInfo original = new RouteInfo("orders", "10.0.0.1", "10.0.0.2");
        String line = LineProtocol.encodeMasterRouteResponse(CommandVerb.SELECT, original);
        TextCommand cmd = LineProtocol.parse(line);

        assertEquals(CommandSource.MASTER, cmd.source());
        assertEquals(CommandVerb.SELECT, cmd.verb());

        RouteInfo decoded = LineProtocol.decodeMasterRouteResponse(cmd);
        assertEquals("orders", decoded.tableName());
        assertEquals("10.0.0.1", decoded.primaryRegion());
        assertEquals("10.0.0.2", decoded.secondaryRegion());
        assertTrue(decoded.hasSecondaryRegion());
    }

    @Test
    void masterRouteResponseWithNoSecondary() {
        RouteInfo original = new RouteInfo("logs", "10.0.0.1", null);
        String line = LineProtocol.encodeMasterRouteResponse(CommandVerb.INSERT, original);
        RouteInfo decoded = LineProtocol.decodeMasterRouteResponse(LineProtocol.parse(line));

        assertFalse(decoded.hasSecondaryRegion());
        assertEquals("logs", decoded.tableName());
    }

    // ---- SHOW TABLES ----

    @Test
    void showTablesEncodeAndDecode() {
        List<String> tables = List.of("orders", "users");
        String line = LineProtocol.encodeMasterShowTables(tables);
        TextCommand cmd = LineProtocol.parse(line);

        List<String> decoded = LineProtocol.decodeMasterShowTables(cmd);
        assertEquals(tables, decoded);
    }

    @Test
    void showTablesEmpty() {
        String line = LineProtocol.encodeMasterShowTables(List.of());
        List<String> decoded = LineProtocol.decodeMasterShowTables(LineProtocol.parse(line));
        assertTrue(decoded.isEmpty());
    }

    // ---- OK / ERROR ----

    @Test
    void encodeAndDetectError() {
        String line = LineProtocol.encodeError("Table does not exist: users");
        TextCommand cmd = LineProtocol.parse(line);
        assertTrue(LineProtocol.isError(cmd));
        // errorMessage 应当包含完整信息（空格拼接）
        String msg = LineProtocol.errorMessage(cmd);
        assertTrue(msg.contains("Table") && msg.contains("users"));
    }

    @Test
    void encodeOkIsNotError() {
        String line = LineProtocol.encodeOk("recover accepted");
        TextCommand cmd = LineProtocol.parse(line);
        assertFalse(LineProtocol.isError(cmd));
        assertEquals(CommandVerb.OK, cmd.verb());
    }

    // ---- Master 管控命令 ----

    @Test
    void encodeMasterRecover() {
        String line = LineProtocol.encodeMasterRecover();
        TextCommand cmd = LineProtocol.parse(line);
        assertEquals(CommandSource.MASTER, cmd.source());
        assertEquals(CommandVerb.RECOVER, cmd.verb());
        assertTrue(cmd.arguments().isEmpty());
    }

    @Test
    void encodeMasterCopy() {
        String line = LineProtocol.encodeMasterCopy("10.0.0.3", "users.txt");
        TextCommand cmd = LineProtocol.parse(line);
        assertEquals(CommandVerb.COPY, cmd.verb());
        assertEquals("10.0.0.3", cmd.argument(0));
        assertEquals("users.txt", cmd.argument(1));
    }

    // ---- 解析异常 ----

    @Test
    void parseNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> LineProtocol.parse(null));
    }

    @Test
    void parseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> LineProtocol.parse("  "));
    }

    @Test
    void parseMissingSourceThrows() {
        assertThrows(IllegalArgumentException.class, () -> LineProtocol.parse("create users"));
    }

    @Test
    void parseUnknownVerbThrows() {
        assertThrows(IllegalArgumentException.class, () -> LineProtocol.parse("[client] unknownverb foo"));
    }
}
