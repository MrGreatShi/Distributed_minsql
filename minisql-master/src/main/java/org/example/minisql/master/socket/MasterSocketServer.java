package org.example.minisql.master.socket;

import org.example.minisql.common.config.MiniSqlConfig;
import org.example.minisql.master.handler.client.ClientCommandHandler;
import org.example.minisql.master.handler.region.RegionCommandHandler;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.TextCommand;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Master 的 TCP Socket 监听器。
 * <p>
 * 监听端口 {@code masterPort}(默认 12345)，同时接受两类连接：
 * <ul>
 *   <li>客户端（{@code [client] ...}）：路由查询 / SHOW TABLES</li>
 *   <li>RegionServer（{@code [region] ...}）：表变更通知 / 启动上报</li>
 * </ul>
 * 每条连接由一条独立线程处理，协议为「一行请求 / 一行响应」的文本行协议。
 */
public final class MasterSocketServer implements AutoCloseable {
    private final MiniSqlConfig config;
    private final ClientCommandHandler clientHandler;
    private final RegionCommandHandler regionHandler;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean running;
    private ServerSocket serverSocket;

    public MasterSocketServer(MiniSqlConfig config, ClientCommandHandler clientHandler, RegionCommandHandler regionHandler) {
        this.config = config;
        this.clientHandler = clientHandler;
        this.regionHandler = regionHandler;
    }

    /** 开始监听，开启客户端连接接收循环。 */
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(config.masterPort()));
        running = true;
        executor.submit(this::acceptLoop);
    }

    /** 主接收循环，每个新连接交由线程池处理。 */
    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                executor.submit(() -> handle(socket));
            } catch (IOException e) {
                if (running) {
                    System.err.printf("Master accept failed: %s%n", e.getMessage());
                }
            }
        }
    }

    private void handle(Socket socket) {
        try (socket) {
            socket.setSoTimeout(config.socketTimeoutMillis());
            try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))
            ) {
                String line = reader.readLine();
                String response = dispatch(line, socket);
                writer.write(response);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            System.err.printf("Master connection failed: %s%n", e.getMessage());
        }
    }

    /**
     * 根据消息源（{@code [client]} 或 {@code [region]}）分发到对应 Handler。
     * 解析失败或源不匹配时返回错误行。
     */
    private String dispatch(String line, Socket socket) {
        try {
            TextCommand command = LineProtocol.parse(line);
            if (command.source() == CommandSource.CLIENT) {
                return clientHandler.handle(command);
            }
            if (command.source() == CommandSource.REGION) {
                return regionHandler.handle(command, socket.getInetAddress().getHostAddress());
            }
            return LineProtocol.encodeError("Master command is not accepted here");
        } catch (RuntimeException e) {
            return LineProtocol.encodeError(e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            }
        }
        executor.shutdownNow();
    }
}
