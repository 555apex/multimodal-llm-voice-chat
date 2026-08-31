package cn.fj.roadagent.boot;

import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.application.port.VehicleTravelPatternPort;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.traffic.RegionalTrafficService;
import cn.fj.roadagent.core.traffic.VehiclePatternService;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 只执行SELECT，核验需求1-5与1-6共享MySQL数据。 */
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
class RegionalAndVehicleReadOnlyIntegrationTest {
    @Autowired private RegionalTrafficDataPort regionalTrafficDataPort;
    @Autowired private VehicleTravelPatternPort vehicleTravelPatternPort;

    @Test
    void loadsValidatedTransportHubsAndLatestCityRows() {
        var regional = regionalTrafficDataPort.load();
        assertFalse(regional.hubs().isEmpty());
        assertEquals(regional.hubs().size(), new HashSet<>(regional.hubs().stream()
                .map(hub -> hub.checkpointNo()).toList()).size());
        assertTrue(regional.hubs().stream().allMatch(hub -> hub.dailyAverageFlow() >= 0));

        var fuzhou = vehicleTravelPatternPort.latestForCity("福州").orElseThrow();
        var xiamen = vehicleTravelPatternPort.latestForCity("厦门").orElseThrow();
        assertEquals("福州", fuzhou.cityName().replace("市", ""));
        assertEquals("厦门", xiamen.cityName().replace("市", ""));
        assertTrue(!fuzhou.acquiredAt().isAfter(java.time.Instant.now()));
        assertTrue(!xiamen.acquiredAt().isAfter(java.time.Instant.now()));
    }

    @Test
    void deterministicServicesProduceExpectedTopLimitsAndTwentyFourHours() {
        RegionalTrafficService regionalService = new RegionalTrafficService(regionalTrafficDataPort, null);
        var regional = regionalService.collectFacts(new HighwayTrafficQuery(
                TrafficQueryType.REGIONAL_TRAFFIC_OVERVIEW, null, null, null, null,
                List.of(), null, "readonly-it"
        ));
        assertTrue(regional.hubRows().size() <= 20);
        assertTrue(regional.regionRows().size() <= 5);
        assertTrue(regional.routeRows().size() <= 10);

        VehiclePatternService vehicleService = new VehiclePatternService(vehicleTravelPatternPort, null);
        var vehicle = vehicleService.collectFacts(new HighwayTrafficQuery(
                TrafficQueryType.VEHICLE_PATTERN_OVERVIEW, null, null, null, null,
                List.of(), "福州市", "readonly-it"
        ));
        assertEquals(3, vehicle.structureRows().size());
        assertEquals(3, vehicle.timeFeatureRows().size());
        assertEquals(3, vehicle.dayTypeRows().size());
        assertEquals(24, vehicle.hourlySeries().size());
    }
}
