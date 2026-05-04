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

public final class RegionClient {
    private final MiniSqlConfig config;

    public RegionClient(MiniSqlConfig config) {
        this.config = config;
    }

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
                String response = reader.readLine();
                if (response == null) {
                    throw new IOException("Region closed connection without response");
                }
                return response;
            }
        }
    }
}
