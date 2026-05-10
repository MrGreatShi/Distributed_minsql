package org.example.minisql.protocol.codec;

import org.example.minisql.protocol.command.CommandSource;
import org.example.minisql.protocol.command.CommandVerb;
import org.example.minisql.protocol.command.TextCommand;
import org.example.minisql.protocol.command.master.RouteInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

public final class LineProtocol {
    private LineProtocol() {
    }

    public static TextCommand parse(String line) {
        if (line == null) {
            throw new IllegalArgumentException("Command line is null");
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Command line is empty");
        }
        if (!trimmed.startsWith("[")) {
            throw new IllegalArgumentException("Command source is missing");
        }
        int close = trimmed.indexOf(']');
        if (close < 0) {
            throw new IllegalArgumentException("Command source is not closed");
        }
        CommandSource source = CommandSource.fromWireName(trimmed.substring(1, close));
        String body = trimmed.substring(close + 1).trim();
        if (body.isEmpty()) {
            throw new IllegalArgumentException("Command verb is missing");
        }
        String[] pieces = body.split("\\s+");
        CommandVerb verb = CommandVerb.fromWireName(pieces[0]);
        List<String> arguments = new ArrayList<>();
        for (int i = 1; i < pieces.length; i++) {
            if (!pieces[i].isBlank()) {
                arguments.add(pieces[i]);
            }
        }
        return new TextCommand(source, verb, arguments, trimmed);
    }

    public static String encode(CommandSource source, CommandVerb verb, Collection<String> arguments) {
        StringJoiner joiner = new StringJoiner(" ");
        joiner.add("[" + source.wireName() + "]");
        joiner.add(verb.wireName());
        for (String argument : arguments) {
            if (argument != null && !argument.isBlank()) {
                joiner.add(argument.trim());
            }
        }
        return joiner.toString();
    }

    public static String encodeClientRouteRequest(CommandVerb verb, String tableName) {
        if (verb == CommandVerb.SHOW) {
            return encode(CommandSource.CLIENT, CommandVerb.SHOW, List.of("tables"));
        }
        return encode(CommandSource.CLIENT, verb, List.of(tableName));
    }

    public static String encodeMasterRouteResponse(CommandVerb verb, RouteInfo route) {
        String secondary = route.hasSecondaryRegion() ? route.secondaryRegion() : "-";
        return encode(CommandSource.MASTER, verb, List.of(route.primaryRegion(), secondary, route.tableName()));
    }

    public static RouteInfo decodeMasterRouteResponse(TextCommand command) {
        if (command.source() != CommandSource.MASTER) {
            throw new IllegalArgumentException("Route response must come from master");
        }
        if (command.arguments().size() < 3) {
            throw new IllegalArgumentException("Route response requires primary, secondary and table");
        }
        String secondary = "-".equals(command.argument(1)) ? null : command.argument(1);
        return new RouteInfo(command.argument(2), command.argument(0), secondary);
    }

    public static String encodeMasterShowTables(Collection<String> tableNames) {
        return encode(CommandSource.MASTER, CommandVerb.SHOW, tableNames);
    }

    public static List<String> decodeMasterShowTables(TextCommand command) {
        if (command.source() != CommandSource.MASTER || command.verb() != CommandVerb.SHOW) {
            throw new IllegalArgumentException("Show tables response must come from master");
        }
        return command.arguments();
    }

    public static String encodeMasterRecover() {
        return encode(CommandSource.MASTER, CommandVerb.RECOVER, List.of());
    }

    public static String encodeMasterCopy(String targetRegion, String tableFileName) {
        return encode(CommandSource.MASTER, CommandVerb.COPY, List.of(targetRegion, tableFileName));
    }

    public static String encodeOk(String message) {
        if (message == null || message.isBlank()) {
            return encode(CommandSource.MASTER, CommandVerb.OK, List.of());
        }
        return encode(CommandSource.MASTER, CommandVerb.OK, List.of(sanitize(message)));
    }

    public static String encodeError(String message) {
        return encode(CommandSource.MASTER, CommandVerb.ERROR, List.of(sanitize(message)));
    }

    public static boolean isError(TextCommand command) {
        return command.source() == CommandSource.MASTER && command.verb() == CommandVerb.ERROR;
    }

    public static String errorMessage(TextCommand command) {
        return String.join(" ", command.arguments());
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown-error";
        }
        return value.replace('\r', ' ').replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }
}
