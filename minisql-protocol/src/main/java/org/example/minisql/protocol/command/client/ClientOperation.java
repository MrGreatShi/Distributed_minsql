package org.example.minisql.protocol.command.client;

import org.example.minisql.protocol.command.CommandVerb;

public enum ClientOperation {
    CREATE_TABLE,
    CREATE_INDEX,
    DROP_TABLE,
    DROP_INDEX,
    SELECT,
    INSERT,
    DELETE,
    SHOW_TABLES;

    public boolean needsRoute() {
        return this != SHOW_TABLES;
    }

    public boolean writesData() {
        return this != SELECT && this != SHOW_TABLES;
    }

    public boolean invalidatesCacheAfterSuccess() {
        return this == DROP_TABLE;
    }

    public CommandVerb routeVerb() {
        return switch (this) {
            case CREATE_TABLE -> CommandVerb.CREATE;
            case DROP_TABLE -> CommandVerb.DROP;
            case SELECT -> CommandVerb.SELECT;
            case INSERT, CREATE_INDEX -> CommandVerb.INSERT;
            case DELETE -> CommandVerb.DELETE;
            case DROP_INDEX -> CommandVerb.DROP;
            case SHOW_TABLES -> CommandVerb.SHOW;
        };
    }
}
