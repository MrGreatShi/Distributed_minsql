package org.example.minisql.common.config;

public final class MiniSqlConfigKeys {
    public static final String MASTER_HOST = "minisql.master.host";
    public static final String ZOOKEEPER_REGISTRY_PATH = "minisql.zookeeper.registry-path";
    public static final String ZOOKEEPER_CONNECT_STRING = "minisql.zookeeper.connect-string";
    public static final String ZOOKEEPER_SESSION_TIMEOUT_MILLIS = "minisql.zookeeper.session-timeout-millis";
    public static final String ZOOKEEPER_CONNECTION_TIMEOUT_MILLIS = "minisql.zookeeper.connection-timeout-millis";
    public static final String MASTER_PORT = "minisql.master.port";
    public static final String REGION_CLIENT_PORT = "minisql.region.client-port";
    public static final String REGION_MIGRATION_PORT = "minisql.region.migration-port";
    public static final String SOCKET_TIMEOUT_MILLIS = "minisql.socket.timeout-millis";

    private MiniSqlConfigKeys() {
    }
}

