package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.domain.dispatch.DispatchScope;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.EmergencyResourceStatus;
import cn.fj.roadagent.domain.dispatch.GeoPoint;
import cn.fj.roadagent.domain.dispatch.ResourceRequirement;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyResourceAllocatorTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private static final EmergencyEvent EVENT = new EmergencyEvent(
            "1", "EVT-1", NOW, "DT01", "福州边坡崩塌", "350100", "福州"
    );

    @Test
    void shouldUseLocalFirstThenNearestCitiesAndPreserveDonorReserve() {
        Map<String, GeoPoint> centers = Map.of(
                "350100", new GeoPoint(0, 0),
                "350200", new GeoPoint(2.1, 0),
                "350500", new GeoPoint(1.5, 0),
                "350600", new GeoPoint(2.4, 0)
        );
        EmergencyResourceAllocator allocator = new EmergencyResourceAllocator(
                (from, to) -> to.longitude() * 100
        );

        ResourceAllocationResult result = allocator.allocate(
                EVENT,
                List.of(requirement(6)),
                List.of(
                        resource("FZ", "350100", "福州", 2, 0),
                        resource("XM", "350200", "厦门", 5, 2),
                        resource("QZ", "350500", "泉州", 3, 1),
                        resource("ZZ", "350600", "漳州", 6, 1)
                ),
                centers,
                "WF-1", "DP-1", 1, NOW
        );

        assertEquals(List.of("FZ", "QZ", "XM"), result.allocations().stream()
                .map(item -> item.resource().resourceId()).toList());
        assertEquals(List.of(2, 2, 2), result.allocations().stream()
                .map(item -> item.resource().quantity()).toList());
        assertEquals(DispatchScope.LOCAL,
                result.allocations().get(0).resource().dispatchScope());
        assertEquals(DispatchScope.CROSS_CITY,
                result.allocations().get(1).resource().dispatchScope());
        assertTrue(result.shortages().isEmpty());

        EmergencyResource updatedQuanzhou = result.updatedResources().stream()
                .filter(item -> item.resourceId().equals("QZ")).findFirst().orElseThrow();
        assertEquals(1, updatedQuanzhou.availableQuantity());
        assertEquals(2, updatedQuanzhou.reservedQuantity());
    }

    @Test
    void shouldUseNearestCityWhenNoLocalResourceExists() {
        Map<String, GeoPoint> centers = Map.of(
                "350100", new GeoPoint(0, 0),
                "350200", new GeoPoint(2.1, 0),
                "350500", new GeoPoint(1.5, 0),
                "350600", new GeoPoint(2.4, 0)
        );
        EmergencyResourceAllocator allocator = new EmergencyResourceAllocator(
                (from, to) -> to.longitude() * 100
        );

        ResourceAllocationResult result = allocator.allocate(
                EVENT,
                List.of(requirement(1)),
                List.of(
                        resource("XM", "350200", "厦门", 5, 2),
                        resource("QZ", "350500", "泉州", 3, 1),
                        resource("ZZ", "350600", "漳州", 6, 1)
                ),
                centers,
                "WF-1", "DP-1", 1, NOW
        );

        assertEquals("QZ", result.allocations().get(0).resource().resourceId());
        assertEquals(DispatchScope.CROSS_CITY,
                result.allocations().get(0).resource().dispatchScope());
        assertTrue(result.shortages().isEmpty());
    }

    @Test
    void shouldReportProvincialShortageInsteadOfOverselling() {
        EmergencyResourceAllocator allocator = new EmergencyResourceAllocator(
                (from, to) -> 100D
        );
        Map<String, GeoPoint> centers = Map.of(
                "350100", new GeoPoint(119.2965, 26.0745),
                "350900", new GeoPoint(119.5482, 26.6656));

        ResourceAllocationResult result = allocator.allocate(
                EVENT,
                List.of(requirement(10)),
                List.of(
                        resource("FZ", "350100", "福州", 1, 0),
                        resource("ND", "350900", "宁德", 3, 2)
                ),
                centers,
                "WF-1", "DP-1", 1, NOW
        );

        assertEquals(2, result.allocations().stream()
                .mapToInt(item -> item.resource().quantity()).sum());
        assertEquals(1, result.shortages().size());
        assertEquals(8, result.shortages().get(0).shortageQuantity());
        assertEquals(2, result.updatedResources().stream()
                .filter(item -> item.resourceId().equals("ND"))
                .findFirst().orElseThrow().availableQuantity());
    }

    private ResourceRequirement requirement(int quantity) {
        return new ResourceRequirement(
                "ROAD_RESCUE_TEAM", "公路抢险队伍", quantity, "组", "道路抢通"
        );
    }

    private EmergencyResource resource(
            String id, String cityCode, String city, int available, int minimumReserve
    ) {
        return new EmergencyResource(
                id, "ROAD_RESCUE_TEAM", "公路抢险队伍", city + "抢险资源池",
                cityCode, city, "组", "道路抢通", List.of("DT01"),
                available, available, 0, 0, minimumReserve,
                EmergencyResourceStatus.ACTIVE, 0
        );
    }
}
