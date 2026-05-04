package org.example.minisql.protocol.command;

import java.util.List;

public record TextCommand(
    CommandSource source,
    CommandVerb verb,
    List<String> arguments,
    String rawLine
) {
    public TextCommand {
        arguments = List.copyOf(arguments);
    }

    public String argument(int index) {
        return arguments.get(index);
    }
}
