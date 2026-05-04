package org.example.minisql.master.handler.region;

import org.example.minisql.master.metadata.TableManager;
import org.example.minisql.protocol.codec.LineProtocol;
import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.TextCommand;

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

    private String handleRecover(TextCommand command, String remoteRegion) {
        tableManager.registerRecoveredTables(remoteRegion, command.arguments());
        return LineProtocol.encodeOk("recover accepted");
    }

    private String handleCreate(TextCommand command, String remoteRegion) {
        if (command.arguments().isEmpty()) {
            return LineProtocol.encodeError("Table name is required");
        }
        tableManager.registerTableOnRegion(remoteRegion, command.argument(0));
        return LineProtocol.encodeOk("create accepted");
    }

    private String handleDrop(TextCommand command) {
        if (command.arguments().isEmpty()) {
            return LineProtocol.encodeError("Table name is required");
        }
        tableManager.dropTable(command.argument(0));
        return LineProtocol.encodeOk("drop accepted");
    }
}
