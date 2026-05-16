package org.example.minisql.client.regionlink;

import org.example.minisql.common.config.MiniSqlConfig;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * 客户端与 RegionServer 的通信封装。
 * <p>
 * 向指定的 Region（IP:regionClientPort）发送 SQL 并返回执行结果。
 * <p>
 * 请求格式：SQL 字符串（以 {@code ;;} 结尾），不带协议头。<br>
 * 响应格式：纯文本结果，多行用空行分隔。
 * <p>
 * 每次调用都建立新 TCP 连接（短连接模式）。
 */
public final class RegionClient {
    private final MiniSqlConfig config;

    public RegionClient(MiniSqlConfig config) {
        this.config = config;
    }

    /**
     * 将 SQL 发往指定 Region 并返回执行结果。
     *
     * @param region Region 的 IP（端口由配置决定）
     * @param sql    SQL 字符串（含 {@code ;;} 结尾符）
     * @return Region 返回的纯文本结果
     * @throws IOException 连接失败或 Region 没有返回任何内容
     */
    public String execute(String region, String sql) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(region, config.regionClientPort()), config.socketTimeoutMillis());
            socket.setSoTimeout(config.socketTimeoutMillis());
            try (
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))
            ) {
                writer.write(sql);
                writer.newLine();
                writer.flush();
                // 读取 Region 返回的多行结果，空行或 EOF 均视为结束信号
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null && !line.isEmpty()) {
                    if (result.length() > 0) {
                        result.append(System.lineSeparator());
                    }
                    result.append(line);
                }
                if (result.length() == 0) {
                    throw new IOException("Region closed connection without response");
                }
                return result.toString();
            }
        }
    }
}
