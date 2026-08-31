package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotPort;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotSource;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** 冷启动立即发布一致性事务读到的首份合法数据，后续更新稳定达到阈值后再原子切换。 */
public final class InMemoryHighwayTrafficSnapshotCache implements HighwayTrafficSnapshotPort, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryHighwayTrafficSnapshotCache.class);

    private final HighwayTrafficSnapshotSource source;
    private final Clock clock;
    private final Duration pollInterval;
    private final Duration stableFor;
    private final ScheduledExecutorService executor;
    private final AtomicReference<HighwayTrafficSnapshot> published = new AtomicReference<>();

    private String candidateFingerprint;
    private Instant candidateFirstSeenAt;

    public InMemoryHighwayTrafficSnapshotCache(
            HighwayTrafficSnapshotSource source,
            Clock clock,
            Duration pollInterval,
            Duration stableFor
    ) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
        this.pollInterval = requirePositive(pollInterval, "轮询间隔");
        this.stableFor = requirePositive(stableFor, "稳定时长");
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mysql-traffic-snapshot-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        executor.scheduleWithFixedDelay(
                this::safeRefresh, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS
        );
    }

    public synchronized void refreshOnce() {
        HighwayTrafficSnapshot candidate;
        try {
            candidate = source.loadCandidate();
        } catch (RuntimeException exception) {
            // 观察窗中任何一次读取失败都意味着“连续稳定”被打断。
            candidateFingerprint = null;
            candidateFirstSeenAt = null;
            throw exception;
        }
        Instant now = clock.instant();
        if (published.get() == null) {
            published.set(candidate);
            candidateFingerprint = candidate.fingerprint();
            candidateFirstSeenAt = now;
            logPublished(candidate);
            return;
        }
        if (!candidate.fingerprint().equals(candidateFingerprint)) {
            candidateFingerprint = candidate.fingerprint();
            candidateFirstSeenAt = now;
            return;
        }
        if (candidateFirstSeenAt != null
                && !now.isBefore(candidateFirstSeenAt.plus(stableFor))) {
            HighwayTrafficSnapshot previous = published.getAndSet(candidate);
            if (previous == null || !previous.fingerprint().equals(candidate.fingerprint())) {
                logPublished(candidate);
            }
        }
    }

    private void logPublished(HighwayTrafficSnapshot candidate) {
        LOG.info("已发布MySQL交通快照：当前有整体状态路线{}条，路段{}条，数据时间{}",
                candidate.routeSummaries().size(), candidate.segments().size(), candidate.acquiredAt());
    }

    @Override
    public HighwayTrafficSnapshot current() {
        HighwayTrafficSnapshot snapshot = published.get();
        if (snapshot == null) {
            throw new ExternalServiceException(
                    "MYSQL_TRAFFIC", "TRAFFIC_DATA_REFRESHING", "交通数据正在初始化，暂时没有可用快照"
            );
        }
        return snapshot;
    }

    private void safeRefresh() {
        try {
            refreshOnce();
        } catch (RuntimeException exception) {
            LOG.warn("本轮MySQL交通快照未发布：{}", exception.getMessage());
        }
    }

    private Duration requirePositive(Duration value, String label) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(label + "必须大于0");
        }
        return value;
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
