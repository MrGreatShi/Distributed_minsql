package org.example.minisql.client.cache;

import org.example.minisql.protocol.command.master.RouteInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RouteCache 单元测试。
 * 覆盖：存取、大小写不敏感、失效、清空、非法入参。
 */
class RouteCacheTest {
    private RouteCache cache;

    @BeforeEach
    void setUp() {
        cache = new RouteCache();
    }

    @Test
    void missCacheReturnsEmpty() {
        assertTrue(cache.get("users").isEmpty());
    }

    @Test
    void putThenGetReturnsRoute() {
        RouteInfo route = new RouteInfo("users", "10.0.0.1", "10.0.0.2");
        cache.put(route);

        Optional<RouteInfo> result = cache.get("users");
        assertTrue(result.isPresent());
        assertEquals("10.0.0.1", result.get().primaryRegion());
    }

    @Test
    void getIsCaseInsensitive() {
        cache.put(new RouteInfo("users", "10.0.0.1", "10.0.0.2"));

        assertTrue(cache.get("USERS").isPresent());
        assertTrue(cache.get("Users").isPresent());
        assertTrue(cache.get("users").isPresent());
    }

    @Test
    void invalidateRemovesEntry() {
        cache.put(new RouteInfo("orders", "10.0.0.1", "10.0.0.2"));
        cache.invalidate("orders");

        assertFalse(cache.get("orders").isPresent());
    }

    @Test
    void invalidateCaseInsensitive() {
        cache.put(new RouteInfo("orders", "10.0.0.1", "10.0.0.2"));
        cache.invalidate("ORDERS");

        assertFalse(cache.get("orders").isPresent());
    }

    @Test
    void invalidateNullOrBlankIsIgnored() {
        // 不应抛出异常
        cache.invalidate(null);
        cache.invalidate("   ");
    }

    @Test
    void clearRemovesAll() {
        cache.put(new RouteInfo("t1", "10.0.0.1", null));
        cache.put(new RouteInfo("t2", "10.0.0.2", null));
        cache.clear();

        assertTrue(cache.get("t1").isEmpty());
        assertTrue(cache.get("t2").isEmpty());
    }

    @Test
    void getWithNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> cache.get(null));
    }

    @Test
    void getWithBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> cache.get("  "));
    }

    @Test
    void putOverwritesPreviousRoute() {
        cache.put(new RouteInfo("users", "10.0.0.1", "10.0.0.2"));
        cache.put(new RouteInfo("users", "10.0.0.3", "10.0.0.4"));

        RouteInfo updated = cache.get("users").orElseThrow();
        assertEquals("10.0.0.3", updated.primaryRegion());
    }
}
