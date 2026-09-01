package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotSource;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.TrafficStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryHighwayTrafficSnapshotCacheTest {

    @Test
    void publishesCompleteBatchImmediatelyWhenDatabaseStoppedChangingBeforeStartup() {
        MutableClock clock = new MutableClock();
        MutableSource source = new MutableSource(snapshot(
                "existing-batch", 60, clock.instant().minus(Duration.ofMinutes(14))
        ));
        var cache = new InMemoryHighwayTrafficSnapshotCache(
                source, clock, Duration.ofSeconds(5), Duration.ofSeconds(30)
        );

        cache.refreshOnce();

        assertEquals("existing-batch", cache.current().fingerprint());
    }

    @Test
    void publishesRecentlyWrittenColdStartBatchAndKeepsOldSnapshotDuringRefresh() {
        MutableClock clock = new MutableClock();
        MutableSource source = new MutableSource(snapshot("batch-a", 60, clock.instant()));
        var cache = new InMemoryHighwayTrafficSnapshotCache(
                source, clock, Duration.ofSeconds(5), Duration.ofSeconds(30)
        );

        cache.refreshOnce();
        assertEquals("batch-a", cache.current().fingerprint());

        source.snapshot = snapshot("batch-b", 25, clock.instant());
        clock.advance(Duration.ofSeconds(5));
        cache.refreshOnce();
        assertEquals("batch-a", cache.current().fingerprint());
        source.failure = new ExternalServiceException("MYSQL_TRAFFIC", "TRAFFIC_DATA_REFRESHING", "半批数据");
        assertThrows(ExternalServiceException.class, cache::refreshOnce);
        assertEquals("batch-a", cache.current().fingerprint());
        source.failure = null;
        clock.advance(Duration.ofSeconds(30));
        cache.refreshOnce();
        assertEquals("batch-a", cache.current().fingerprint());
        clock.advance(Duration.ofSeconds(30));
        cache.refreshOnce();
        assertEquals("batch-b", cache.current().fingerprint());
    }

    @Test
    void doesNotBypassStabilityWindowForLaterUpdatesEvenWhenTimestampIsOld() {
        MutableClock clock = new MutableClock();
        MutableSource source = new MutableSource(snapshot(
                "batch-a", 60, clock.instant().minus(Duration.ofMinutes(14))
        ));
        var cache = new InMemoryHighwayTrafficSnapshotCache(
                source, clock, Duration.ofSeconds(5), Duration.ofSeconds(30)
        );
        cache.refreshOnce();
        assertEquals("batch-a", cache.current().fingerprint());

        source.snapshot = snapshot("batch-b", 25, clock.instant().minus(Duration.ofMinutes(1)));
        cache.refreshOnce();
        assertEquals("batch-a", cache.current().fingerprint());
        clock.advance(Duration.ofSeconds(30));
        cache.refreshOnce();
        assertEquals("batch-b", cache.current().fingerprint());
    }

    private HighwayTrafficSnapshot snapshot(String fingerprint, double speed, Instant acquiredAt) {
        return new HighwayTrafficSnapshot(
                java.util.List.of(new HighwayRoute("G104", "北京-平潭", "国道", "宁德市", "福州市")),
                java.util.List.of(new RouteTrafficSummary("G104", "北京-平潭", speed, TrafficStatus.SMOOTH)),
                java.util.List.of(new HighwayTrafficSegment(
                        "G104", "北京-平潭", "FJ001→FJ002", 10d, speed, TrafficStatus.SMOOTH, 0.1
                )),
                acquiredAt, fingerprint
        );
    }

    private static final class MutableSource implements HighwayTrafficSnapshotSource {
        private HighwayTrafficSnapshot snapshot;
        private RuntimeException failure;

        private MutableSource(HighwayTrafficSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public HighwayTrafficSnapshot loadCandidate() {
            if (failure != null) throw failure;
            return snapshot;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-13T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
