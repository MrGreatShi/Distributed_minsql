package org.example.minisql.client.masterlink;

import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.example.minisql.protocol.command.master.RouteInfo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 客户端与 Master 的通信封装。
 * <p>
 * 每次调用都建立一条短连接（请求→响应→关闭），不保持长连接。
 * <p>
 * 协议格式：一行请求 / 一行响应，具体语法见 {@link org.example.minisql.protocol.codec.LineProtocol}。
 * 请求示例：{@code [client] create users}
 * 响应示例：{@code [master] create 10.0.0.1 10.0.0.2 users}
 */
public final class MasterClient {
    private final MiniSqlConfig config;

    public MasterClient(MiniSqlConfig config) {
        this.config = config;
    }

    /**
     * 向 Master 查询指定表和操作类型对应的 Region 路由。
     *
     * @param verb      发送给 Master 的操作动词（create/select/insert/delete/drop）
     * @param tableName 表名
     * @return 主副 Region 路由信息
     * @throws IOException Master 返回错误或网络异常
     */
    public RouteInfo queryRoute(CommandVerb verb, String tableName) throws IOException {
        String response = exchange(LineProtocol.encodeClientRouteRequest(verb, tableName));
        TextCommand command = LineProtocol.parse(response);
        if (LineProtocol.isError(command)) {
            throw new IOException(LineProtocol.errorMessage(command));
        }
        if (command.verb() != verb) {
            throw new IOException("Unexpected master response: " + response);
        }
        return LineProtocol.decodeMasterRouteResponse(command);
    }

    /**
     * 向 Master 查询当前所有表名。
     *
     * @return 表名列表（已按字典序排序）
     * @throws IOException Master 返回错误或网络异常
     */
    public List<String> showTables() throws IOException {
        String response = exchange(LineProtocol.encodeClientRouteRequest(CommandVerb.SHOW, null));
        TextCommand command = LineProtocol.parse(response);
        if (LineProtocol.isError(command)) {
            throw new IOException(LineProtocol.errorMessage(command));
        }
        return LineProtocol.decodeMasterShowTables(command);
    }

    /**
     * 与 Master 建立一次性 TCP 连接，发送请求并读取一行响应。
     * 连接在方法返回后自动关闭。
     */
    private String exchange(String request) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.masterHost(), config.masterPort()), config.socketTimeoutMillis());
            socket.setSoTimeout(config.socketTimeoutMillis());
            try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                writer.write(request);
                writer.newLine();
                writer.flush();
                String response = reader.readLine();
                if (response == null) {
                    throw new IOException("Master closed connection without response");
                }
                return response;
            }
        }
    }
}
