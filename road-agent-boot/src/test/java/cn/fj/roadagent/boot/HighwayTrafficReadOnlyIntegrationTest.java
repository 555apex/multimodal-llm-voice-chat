package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.traffic.mysql.InMemoryHighwayTrafficSnapshotCache;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotSource;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.traffic.HighwayTrafficService;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅执行SELECT，不修改共享MySQL数据。 */
@EnabledIfEnvironmentVariable(named = "ROADAGENT_DB_URL", matches = ".+")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "roadagent.traffic.snapshot-poll-seconds=60",
                "roadagent.traffic.snapshot-stable-seconds=30",
                "roadagent.model.provider=openai-compatible",
                "roadagent.model.api-key=test-model-key"
        }
)
class HighwayTrafficReadOnlyIntegrationTest {

    @Autowired
    private HighwayTrafficSnapshotSource source;

    @Test
    void loadsAvailableValidatedSnapshotFromTheThreeTrafficTables() {
        var snapshot = source.loadCandidate();

        assertFalse(snapshot.routes().isEmpty());
        Set<String> activeCodes = snapshot.routes().stream()
                .map(route -> route.routeCode()).collect(Collectors.toSet());
        assertTrue(snapshot.routeSummaries().stream()
                .allMatch(summary -> activeCodes.contains(summary.routeCode())));
        assertTrue(snapshot.segments().stream()
                .allMatch(segment -> activeCodes.contains(segment.routeCode())));
        assertTrue(snapshot.routes().stream()
                .allMatch(route -> route.routeCode().startsWith("G") || route.routeCode().startsWith("S")));
        assertTrue(snapshot.segments().stream()
                .allMatch(segment -> segment.severity() >= 0 && segment.severity() <= 1));

        if (snapshot.acquiredAt().isBefore(Clock.systemUTC().instant().minusSeconds(30))) {
            try (var cache = new InMemoryHighwayTrafficSnapshotCache(
                    source, Clock.systemUTC(), Duration.ofSeconds(5), Duration.ofSeconds(30)
            )) {
                cache.refreshOnce();
                assertEquals(snapshot.fingerprint(), cache.current().fingerprint());
            }
        }

        HighwayTrafficService service = new HighwayTrafficService(() -> snapshot, null);
        var overview = service.collectFacts(new HighwayTrafficQuery(
                TrafficQueryType.PROVINCE_OVERVIEW, null, null, null, null, "readonly-it"
        ));
        var abnormal = service.collectFacts(new HighwayTrafficQuery(
                TrafficQueryType.PROVINCE_ABNORMAL, null, null, null, null, "readonly-it"
        ));
        var cityPair = service.collectFacts(new HighwayTrafficQuery(
                TrafficQueryType.CITY_PAIR, "宁德市", "福州市", null, null, "readonly-it"
        ));
        var routeDetail = service.collectFacts(new HighwayTrafficQuery(
                TrafficQueryType.ROUTE_DETAIL, null, null, "G104", null, "readonly-it"
        ));

        assertEquals(snapshot.routeSummaries().size(), overview.routeSummaries().size());
        assertTrue(abnormal.segments().size() <= 10);
        assertTrue(abnormal.segments().stream().allMatch(segment -> segment.status().abnormal()));
        assertTrue(cityPair.segments().size() <= 20);
        assertTrue(routeDetail.segments().size() <= 20);
        assertTrue(routeDetail.routeSummaries().stream()
                .allMatch(summary -> summary.routeCode().equals("G104")));
    }
}
