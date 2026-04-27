package org.example.minisql.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public final class MiniSqlConfigLoader {
    private static final String DEFAULT_RESOURCE = "minisql-default.properties";

    private MiniSqlConfigLoader() {
    }

    public static MiniSqlConfig load() {
        Properties properties = new Properties();
        loadDefaultsFromResource(properties);
        overrideWithSystemProperties(properties);

        return new MiniSqlConfig(
            properties.getProperty(MiniSqlConfigKeys.ZOOKEEPER_REGISTRY_PATH, "/db"),
            parseInt(properties.getProperty(MiniSqlConfigKeys.MASTER_PORT), 12345),
            parseInt(properties.getProperty(MiniSqlConfigKeys.REGION_CLIENT_PORT), 22222),
            parseInt(properties.getProperty(MiniSqlConfigKeys.REGION_MIGRATION_PORT), 1117)
        );
    }

    private static void loadDefaultsFromResource(Properties target) {
        try (InputStream input = MiniSqlConfigLoader.class.getClassLoader().getResourceAsStream(DEFAULT_RESOURCE)) {
            if (input != null) {
                target.load(input);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load default config: " + DEFAULT_RESOURCE, e);
        }
    }

    private static void overrideWithSystemProperties(Properties target) {
        override(target, MiniSqlConfigKeys.ZOOKEEPER_REGISTRY_PATH);
        override(target, MiniSqlConfigKeys.MASTER_PORT);
        override(target, MiniSqlConfigKeys.REGION_CLIENT_PORT);
        override(target, MiniSqlConfigKeys.REGION_MIGRATION_PORT);
    }

    private static void override(Properties target, String key) {
        String value = System.getProperty(key);
        if (value != null && !value.isBlank()) {
            target.setProperty(key, value.trim());
        }
    }

    private static int parseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }
}

