package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotPort;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotSource;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
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

/** 通行能力专用快照缓存，不影响现有交通状态快照。 */
public final class InMemoryRoadCapacitySnapshotCache implements RoadCapacitySnapshotPort, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(InMemoryRoadCapacitySnapshotCache.class);

    private final RoadCapacitySnapshotSource source;
    private final Clock clock;
    private final Duration pollInterval;
    private final Duration stableFor;
    private final ScheduledExecutorService executor;
    private final AtomicReference<RoadCapacitySnapshot> published = new AtomicReference<>();

    private String candidateFingerprint;
    private Instant candidateFirstSeenAt;

    public InMemoryRoadCapacitySnapshotCache(
            RoadCapacitySnapshotSource source,
            Clock clock,
            Duration pollInterval,
            Duration stableFor
    ) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
        this.pollInterval = requirePositive(pollInterval, "轮询间隔");
        this.stableFor = requirePositive(stableFor, "稳定时长");
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mysql-road-capacity-snapshot-refresh");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start() {
        executor.scheduleWithFixedDelay(this::safeRefresh, 0, pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized void refreshOnce() {
        RoadCapacitySnapshot candidate;
        try {
            candidate = source.loadCandidate();
        } catch (RuntimeException exception) {
            candidateFingerprint = null;
            candidateFirstSeenAt = null;
            throw exception;
        }
        Instant now = clock.instant();
        if (published.get() == null) {
            publish(candidate);
            candidateFingerprint = candidate.fingerprint();
            candidateFirstSeenAt = now;
            return;
        }
        if (!candidate.fingerprint().equals(candidateFingerprint)) {
            candidateFingerprint = candidate.fingerprint();
            candidateFirstSeenAt = now;
            return;
        }
        if (candidateFirstSeenAt != null && !now.isBefore(candidateFirstSeenAt.plus(stableFor))) {
            RoadCapacitySnapshot previous = published.getAndSet(candidate);
            if (previous == null || !previous.fingerprint().equals(candidate.fingerprint())) {
                logPublished(candidate);
            }
        }
    }

    private void publish(RoadCapacitySnapshot candidate) {
        published.set(candidate);
        logPublished(candidate);
    }

    private void logPublished(RoadCapacitySnapshot candidate) {
        LOG.info("已发布MySQL通行能力快照：当前有数据路线{}条，数据时间{}",
                candidate.capacities().size(), candidate.acquiredAt());
    }

    @Override
    public RoadCapacitySnapshot current() {
        RoadCapacitySnapshot snapshot = published.get();
        if (snapshot == null) {
            throw new ExternalServiceException(
                    "MYSQL_ROAD_CAPACITY", "ROAD_CAPACITY_DATA_REFRESHING",
                    "道路通行能力数据正在更新，暂时没有可用的完整快照"
            );
        }
        return snapshot;
    }

    private void safeRefresh() {
        try {
            refreshOnce();
        } catch (RuntimeException exception) {
            LOG.warn("本轮MySQL通行能力快照未发布：{}", exception.getMessage());
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
