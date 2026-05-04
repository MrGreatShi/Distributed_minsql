package org.example.minisql.protocol.command;

import java.util.Locale;

public enum CommandSource {
    CLIENT("client"),
    MASTER("master"),
    REGION("region");

    private final String wireName;

    CommandSource(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static CommandSource fromWireName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CommandSource source : values()) {
            if (source.wireName.equals(normalized)) {
                return source;
            }
        }
        throw new IllegalArgumentException("Unsupported command source: " + value);
    }
}
