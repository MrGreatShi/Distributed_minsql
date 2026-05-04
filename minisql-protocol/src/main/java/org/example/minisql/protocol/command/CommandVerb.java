package org.example.minisql.protocol.command;

import java.util.Locale;

public enum CommandVerb {
    CREATE("create"),
    SELECT("select"),
    INSERT("insert"),
    DELETE("delete"),
    DROP("drop"),
    SHOW("show"),
    RECOVER("recover"),
    COPY("copy"),
    OK("ok"),
    ERROR("error");

    private final String wireName;

    CommandVerb(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static CommandVerb fromWireName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (CommandVerb verb : values()) {
            if (verb.wireName.equals(normalized)) {
                return verb;
            }
        }
        throw new IllegalArgumentException("Unsupported command verb: " + value);
    }
}
