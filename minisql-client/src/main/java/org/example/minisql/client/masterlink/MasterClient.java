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

public final class MasterClient {
    private final MiniSqlConfig config;

    public MasterClient(MiniSqlConfig config) {
        this.config = config;
    }

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

    public List<String> showTables() throws IOException {
        String response = exchange(LineProtocol.encodeClientRouteRequest(CommandVerb.SHOW, null));
        TextCommand command = LineProtocol.parse(response);
        if (LineProtocol.isError(command)) {
            throw new IOException(LineProtocol.errorMessage(command));
        }
        return LineProtocol.decodeMasterShowTables(command);
    }

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
