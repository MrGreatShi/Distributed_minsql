package org.example.minisql.master.metadata;

import org.example.minisql.protocol.command.master.RouteInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableManagerTest {
    @Test
    void createsTwoReplicaRouteOnLeastLoadedRegions() {
        TableManager manager = new TableManager();
        manager.markRegionAvailable("10.0.0.1");
        manager.markRegionAvailable("10.0.0.2");
        RouteInfo route = manager.ensureRouteForCreate("Users");
        assertEquals("users", route.tableName());
        assertTrue(route.hasSecondaryRegion());
        assertEquals(List.of("users"), manager.tableNames());
    }

    @Test
    void requiresTwoHealthyRegionsForCreate() {
        TableManager manager = new TableManager();
        manager.markRegionAvailable("10.0.0.1");
        assertThrows(IllegalStateException.class, () -> manager.ensureRouteForCreate("users"));
    }

    @Test
    void plansRecoveryWhenReplicaIsLost() {
        TableManager manager = new TableManager();
        manager.markRegionAvailable("10.0.0.1");
        manager.markRegionAvailable("10.0.0.2");
        manager.markRegionAvailable("10.0.0.3");
        manager.ensureRouteForCreate("users");
        List<CopyTask> tasks = manager.markRegionUnavailableAndPlanRecovery("10.0.0.1");
        assertFalse(tasks.isEmpty());
        assertEquals("users", tasks.get(0).tableName());
        assertTrue(manager.routeOf("users").orElseThrow().hasSecondaryRegion());
    }
}
