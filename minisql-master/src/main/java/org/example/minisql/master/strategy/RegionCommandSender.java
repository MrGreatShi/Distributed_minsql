package org.example.minisql.master.strategy;

import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.TextCommand;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Master 主动向 RegionServer 发送管控命令的工具类。
 * <p>
 * 所有命令均连接到 Region 的 {@code regionClientPort}(默认 22222)，
 * Region 的 socket 服务器需要能够识别 {@code [master]} 头的管控命令与普通 SQL 请求。
 * <p>
 * 目前支持两种命令：
 * <ul>
 *   <li>{@code [master] recover} — 要求 Region 清空本地数据并重新待命</li>
 *   <li>{@code [master] copy &lt;targetIP&gt; &lt;table&gt;.txt} — 要求 Region 将指定表的日志文件推送到目标节点</li>
 * </ul>
 */
public final class RegionCommandSender {
    private final MiniSqlConfig config;

    public RegionCommandSender(MiniSqlConfig config) {
        this.config = config;
    }

    /** 向指定 Region 发送 recover 命令，令其清空本地数据并重新待命。 */
    public void sendRecover(String region) {
        send(region, LineProtocol.encodeMasterRecover());
    }

    /** 向 sourceRegion 发送 copy 命令，令其将 &lt;table&gt;.txt 推送到 targetRegion。 */
    public void sendCopy(String sourceRegion, String targetRegion, String tableName) {
        send(sourceRegion, LineProtocol.encodeMasterCopy(targetRegion, tableName + ".txt"));
    }

    /**
     * 建立一次性 TCP 连接向 Region 发送命令，并读取响应。
     * 连接失败或 Region 返回错误时均仅打印日志，不抛出异常。
     */
    private void send(String region, String command) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(region, config.regionClientPort()), config.socketTimeoutMillis());
            socket.setSoTimeout(config.socketTimeoutMillis());
            try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                writer.write(command);
                writer.newLine();
                writer.flush();
                String response = reader.readLine();
                if (response != null) {
                    try {
                        TextCommand reply = LineProtocol.parse(response);
                        if (LineProtocol.isError(reply)) {
                            System.err.printf("Region %s rejected command '%s': %s%n",
                                region, command, LineProtocol.errorMessage(reply));
                        }
                    } catch (IllegalArgumentException ignored) {
                        System.err.printf("Region %s returned unexpected response for '%s': %s%n",
                            region, command, response);
                    }
                }
            }
        } catch (IOException e) {
            System.err.printf("Failed to send command to region %s: %s%n", region, e.getMessage());
        }
    }
}
