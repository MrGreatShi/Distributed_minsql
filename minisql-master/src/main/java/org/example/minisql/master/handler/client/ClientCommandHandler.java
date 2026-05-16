package org.example.minisql.master.handler.client;

import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.example.minisql.protocol.command.master.RouteInfo;

/**
 * 处理客户端发向 Master 的路由查询和建表请求。
 * <p>
 * 支持的客户端命令及响应：
 * <pre>
 * [client] show tables        → [master] show t1 t2 ...
 * [client] create &lt;table&gt;    → [master] create &lt;主IP&gt; &lt;副IP&gt; &lt;table&gt;
 * [client] select &lt;table&gt;    → [master] select &lt;主IP&gt; &lt;副IP&gt; &lt;table&gt;
 * [client] insert &lt;table&gt;    → [master] insert &lt;主IP&gt; &lt;副IP&gt; &lt;table&gt;
 * [client] delete &lt;table&gt;    → [master] delete &lt;主IP&gt; &lt;副IP&gt; &lt;table&gt;
 * [client] drop   &lt;table&gt;    → [master] drop   &lt;主IP&gt; &lt;副IP&gt; &lt;table&gt;
 * </pre>
 * 建表时 Master 负责选择负载最小的两个 Region 作为主副副本。
 */
public final class ClientCommandHandler {
    private final TableManager tableManager;

    public ClientCommandHandler(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    public String handle(TextCommand command) {
        if (command.source() != CommandSource.CLIENT) {
            return LineProtocol.encodeError("Expected client command");
        }
        try {
            return switch (command.verb()) {
                case SHOW -> handleShow(command);
                case CREATE -> handleCreate(command);
                case SELECT, INSERT, DELETE, DROP -> handleRouteLookup(command);
                default -> LineProtocol.encodeError("Unsupported client command " + command.verb().wireName());
            };
        } catch (RuntimeException e) {
            return LineProtocol.encodeError(e.getMessage());
        }
    }

    private String handleShow(TextCommand command) {
        if (!command.arguments().isEmpty() && !"tables".equalsIgnoreCase(command.argument(0))) {
            return LineProtocol.encodeError("Only show tables is supported");
        }
        return LineProtocol.encodeMasterShowTables(tableManager.tableNames());
    }

    /**
     * 处理 create 命令：让 Master 选择两个 Region，建立路由条目。
     * 若当前健康 Region 数 &lt; 2，返回错误。
     */
    private String handleCreate(TextCommand command) {
        String tableName = requiredTableName(command);
        RouteInfo route = tableManager.ensureRouteForCreate(tableName);
        return LineProtocol.encodeMasterRouteResponse(CommandVerb.CREATE, route);
    }

    /**
     * 处理路由查询（select/insert/delete/drop）：在已有路由表里查找。
     * 若表不存在，返回错误。
     */
    private String handleRouteLookup(TextCommand command) {
        String tableName = requiredTableName(command);
        RouteInfo route = tableManager.routeOf(tableName)
            .orElseThrow(() -> new IllegalStateException("Table does not exist: " + tableName));
        return LineProtocol.encodeMasterRouteResponse(command.verb(), route);
    }

    private String requiredTableName(TextCommand command) {
        if (command.arguments().isEmpty()) {
            throw new IllegalArgumentException("Table name is required");
        }
        return command.argument(0);
    }
}
