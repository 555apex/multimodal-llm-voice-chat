package cn.fj.roadagent.boot;

import cn.fj.roadagent.application.port.RoadCapacitySnapshotSource;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.traffic.RoadCapacityService;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 仅执行SELECT，验证共享MySQL中的最新通行能力批次。 */
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
class RoadCapacityReadOnlyIntegrationTest {

    @Autowired
    private RoadCapacitySnapshotSource source;

    @Test
    void loadsAvailableValidatedRoutesAndSupportsCapacityQueries() {
        var snapshot = source.loadCandidate();

        assertTrue(snapshot.capacities().stream().allMatch(value ->
                value.actualCapacityVph() >= 0
                        && value.designCapacityVph() >= 0
                        && value.utilizationRatio() >= 0
                        && value.utilizationRatio() <= 1));

        RoadCapacityService service = new RoadCapacityService(() -> snapshot, null);
        var overview = service.collectFacts(query(TrafficQueryType.CAPACITY_OVERVIEW, null));
        var bottlenecks = service.collectFacts(query(TrafficQueryType.CAPACITY_BOTTLENECKS, null));

        assertEquals(snapshot.capacities().size(), overview.rows().size());
        assertTrue(bottlenecks.rows().size() <= 10);
        assertTrue(bottlenecks.rows().stream().allMatch(value -> value.utilizationRatio() < 0.8));
        if (!snapshot.capacities().isEmpty()) {
            String availableCode = snapshot.capacities().get(0).routeCode();
            var detail = service.collectFacts(query(TrafficQueryType.CAPACITY_ROUTE_DETAIL, availableCode));
            assertEquals(availableCode, detail.rows().get(0).routeCode());
        }
    }

    private HighwayTrafficQuery query(TrafficQueryType type, String routeCode) {
        return new HighwayTrafficQuery(type, null, null, routeCode, null, "capacity-readonly-it");
    }
}
