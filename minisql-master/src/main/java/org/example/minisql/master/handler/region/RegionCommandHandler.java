package org.example.minisql.master.handler.region;

import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.TextCommand;

/**
 * 处理 RegionServer 发向 Master 的元数据变更通知。
 * <p>
 * Region 发送的命令及 Master 处理逻辑：
 * <pre>
 * [region] recover &lt;t1&gt; &lt;t2&gt; ...  → 注册该 Region 当前持有的表，更新内存路由表
 * [region] create  &lt;table&gt;         → 记录该 Region 新增了一张表
 * [region] drop    &lt;table&gt;         → 从路由表删除该表
 * </pre>
 * 注：region 的 IP 从 TCP 连接的对端地址获取，而非命令文本中的字段。
 */
public final class RegionCommandHandler {
    private final TableManager tableManager;

    public RegionCommandHandler(TableManager tableManager) {
        this.tableManager = tableManager;
    }

    public String handle(TextCommand command, String remoteRegion) {
        if (command.source() != CommandSource.REGION) {
            return LineProtocol.encodeError("Expected region command");
        }
        try {
            return switch (command.verb()) {
                case RECOVER -> handleRecover(command, remoteRegion);
                case CREATE -> handleCreate(command, remoteRegion);
                case DROP -> handleDrop(command);
                default -> LineProtocol.encodeError("Unsupported region command " + command.verb().wireName());
            };
        } catch (RuntimeException e) {
            return LineProtocol.encodeError(e.getMessage());
        }
    }

    /**
     * Region 启动或重连后主动上报持有的表列表。
     * Master 将这些表注册到路由表中，并将该 Region 标记为健康。
     */
    private String handleRecover(TextCommand command, String remoteRegion) {
        tableManager.registerRecoveredTables(remoteRegion, command.arguments());
        return LineProtocol.encodeOk("recover accepted");
    }

    /**
     * Region 新建了一张表后通知 Master，以便更新全局路由表。
     */
    private String handleCreate(TextCommand command, String remoteRegion) {
        if (command.arguments().isEmpty()) {
            return LineProtocol.encodeError("Table name is required");
        }
        tableManager.registerTableOnRegion(remoteRegion, command.argument(0));
        return LineProtocol.encodeOk("create accepted");
    }

    /**
     * Region 删除了一张表后通知 Master，以便将其从全局路由表中移除。
     */
    private String handleDrop(TextCommand command) {
        if (command.arguments().isEmpty()) {
            return LineProtocol.encodeError("Table name is required");
        }
        tableManager.dropTable(command.argument(0));
        return LineProtocol.encodeOk("drop accepted");
    }
}
