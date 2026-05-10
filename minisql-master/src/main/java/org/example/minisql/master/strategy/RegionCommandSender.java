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

public final class RegionCommandSender {
    private final MiniSqlConfig config;

    public RegionCommandSender(MiniSqlConfig config) {
        this.config = config;
    }

    public void sendRecover(String region) {
        send(region, LineProtocol.encodeMasterRecover());
    }

    public void sendCopy(String sourceRegion, String targetRegion, String tableName) {
        send(sourceRegion, LineProtocol.encodeMasterCopy(targetRegion, tableName + ".txt"));
    }

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
