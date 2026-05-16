package org.example.minisql.master.strategy;

import org.example.minisql.master.metadata.CopyTask;
import org.example.minisql.master.metadata.TableManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Region 生命周期策略管理器。
 * <p>
 * 由 ZooKeeper 监听器触发，处理两种事件：
 * <ul>
 *   <li>{@link #onRegionAvailable(String)} — Region 上线/恢复：
 *       判断是新节点（ADD）还是恢复节点（RECOVER），
 *       都向 Region 发送 {@code [master] recover}（清空本地旧数据）</li>
 *   <li>{@link #onRegionLost(String)} — Region 宕机：
 *       计算负责的表并制定刑除计划，
 *       异步向幸存节点发送 {@code [master] copy} 命令让其将数据推送到新副本。</li>
 * </ul>
 */
public final class RegionLifecycleService implements AutoCloseable {
    private final TableManager tableManager;
    private final RegionCommandSender commandSender;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public RegionLifecycleService(TableManager tableManager, RegionCommandSender commandSender) {
        this.tableManager = tableManager;
        this.commandSender = commandSender;
    }

    /**
     * ZooKeeper 监听到 Region 节点出现时触发。
     * <ul>
     *   <li>首次出现 → ADD 策略：将该 Region 加入集群并发 recover</li>
     *   <li>再次出现 → RECOVER 策略：发 recover 令节点重新待命</li>
     * </ul>
     */
    public void onRegionAvailable(String region) {
        String strategy = tableManager.isKnownRegion(region) ? "RECOVER" : "ADD";
        tableManager.markRegionAvailable(region);
        System.out.printf("[%s] Region %s detected, applying %s strategy%n",
            java.time.LocalTime.now(), region, strategy);
        executor.submit(() -> commandSender.sendRecover(region));
    }

    /**
     * ZooKeeper 监听到 Region 节点消失时触发。
     * 计算罗受影响的表并异步执行数据备份（向幸存副本发 copy 命令）。
     */
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
