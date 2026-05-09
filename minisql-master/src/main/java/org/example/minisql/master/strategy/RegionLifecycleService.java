package org.example.minisql.master.strategy;

import org.example.minisql.master.metadata.CopyTask;
import org.example.minisql.master.metadata.TableManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RegionLifecycleService implements AutoCloseable {
    private final TableManager tableManager;
    private final RegionCommandSender commandSender;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public RegionLifecycleService(TableManager tableManager, RegionCommandSender commandSender) {
        this.tableManager = tableManager;
        this.commandSender = commandSender;
    }

    public void onRegionAvailable(String region) {
        String strategy = tableManager.isKnownRegion(region) ? "RECOVER" : "ADD";
        tableManager.markRegionAvailable(region);
        System.out.printf("[%s] Region %s detected, applying %s strategy%n",
            java.time.LocalTime.now(), region, strategy);
        executor.submit(() -> commandSender.sendRecover(region));
    }

    public void onRegionLost(String region) {
        List<CopyTask> tasks = tableManager.markRegionUnavailableAndPlanRecovery(region);
        for (CopyTask task : tasks) {
            executor.submit(() -> commandSender.sendCopy(task.sourceRegion(), task.targetRegion(), task.tableName()));
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
