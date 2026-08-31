package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotSource;
import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryRoadCapacitySnapshotCacheTest {

    @Test
    void coldStartPublishesExistingBatchImmediately() {
        MutableClock clock = new MutableClock();
        MutableSource source = new MutableSource(snapshot("old", 0, clock.instant().minusSeconds(60)));
        var cache = cache(source, clock);

        cache.refreshOnce();

        assertEquals("old", cache.current().fingerprint());
    }

    @Test
    void keepsOldSnapshotWhileNewBatchStabilizesOrFails() {
        MutableClock clock = new MutableClock();
        MutableSource source = new MutableSource(snapshot("old", 0, clock.instant().minusSeconds(60)));
        var cache = cache(source, clock);
        cache.refreshOnce();

        source.snapshot = snapshot("new", 600, clock.instant());
        cache.refreshOnce();
        assertEquals("old", cache.current().fingerprint());
        source.failure = new ExternalServiceException(
                "MYSQL_ROAD_CAPACITY", "ROAD_CAPACITY_DATA_REFRESHING", "半批数据"
        );
        assertThrows(ExternalServiceException.class, cache::refreshOnce);
        assertEquals("old", cache.current().fingerprint());

        source.failure = null;
        clock.advance(Duration.ofSeconds(30));
        cache.refreshOnce();
        assertEquals("old", cache.current().fingerprint());
        clock.advance(Duration.ofSeconds(30));
        cache.refreshOnce();
        assertEquals("new", cache.current().fingerprint());
    }

    @Test
    void recentColdStartBatchIsPublishedImmediately() {
        MutableClock clock = new MutableClock();
        MutableSource source = new MutableSource(snapshot("new", 300, clock.instant()));
        var cache = cache(source, clock);

        cache.refreshOnce();
        assertEquals("new", cache.current().fingerprint());
    }

    private InMemoryRoadCapacitySnapshotCache cache(MutableSource source, MutableClock clock) {
        return new InMemoryRoadCapacitySnapshotCache(
                source, clock, Duration.ofSeconds(5), Duration.ofSeconds(30)
        );
    }

    private RoadCapacitySnapshot snapshot(String fingerprint, double actual, Instant acquiredAt) {
        return new RoadCapacitySnapshot(
                List.of(new RoadCapacity("G104", "北京-平潭", actual, 1920, actual / 1920)),
                acquiredAt, fingerprint
        );
    }

    private static final class MutableSource implements RoadCapacitySnapshotSource {
        private RoadCapacitySnapshot snapshot;
        private RuntimeException failure;

        private MutableSource(RoadCapacitySnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public RoadCapacitySnapshot loadCandidate() {
            if (failure != null) throw failure;
            return snapshot;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-08-13T00:00:00Z");

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
