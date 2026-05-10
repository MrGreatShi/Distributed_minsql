package org.example.minisql.master.handler.client;

import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.example.minisql.protocol.command.master.RouteInfo;

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

    private String handleCreate(TextCommand command) {
        String tableName = requiredTableName(command);
        RouteInfo route = tableManager.ensureRouteForCreate(tableName);
        return LineProtocol.encodeMasterRouteResponse(CommandVerb.CREATE, route);
    }

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
