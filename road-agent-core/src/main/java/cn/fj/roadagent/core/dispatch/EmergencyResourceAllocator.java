package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.port.CityDistancePort;
import cn.fj.roadagent.domain.dispatch.AllocatedResource;
import cn.fj.roadagent.domain.dispatch.DispatchScope;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.GeoPoint;
import cn.fj.roadagent.domain.dispatch.ResourceAllocation;
import cn.fj.roadagent.domain.dispatch.ResourceRequirement;
import cn.fj.roadagent.domain.dispatch.ResourceShortage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 同城优先、跨市按城市级估算距离并保留来源城市最低库存。 */
public final class EmergencyResourceAllocator {
    private final CityDistancePort cityDistancePort;

    public EmergencyResourceAllocator(CityDistancePort cityDistancePort) {
        this.cityDistancePort = Objects.requireNonNull(cityDistancePort);
    }

    ResourceAllocationResult allocate(
            EmergencyEvent event,
            List<ResourceRequirement> requirements,
            List<EmergencyResource> lockedResources,
            Map<String, GeoPoint> cityCenters,
            String workflowId,
            String planId,
            long planVersion,
            Instant now
    ) {
        if (!event.hasStructuredCity()) {
            throw new IllegalArgumentException("事件缺少结构化城市，无法进行就近资源调度");
        }
        GeoPoint eventCenter = requireCenter(cityCenters, event.cityCode());
        Map<String, EmergencyResource> current = new LinkedHashMap<>();
        lockedResources.stream()
                .sorted(Comparator.comparing(EmergencyResource::resourceId))
                .forEach(item -> current.put(item.resourceId(), item));
        Map<String, EmergencyResource> originals = new LinkedHashMap<>(current);
        List<ResourceAllocation> allocations = new ArrayList<>();
        List<ResourceShortage> shortages = new ArrayList<>();

        for (ResourceRequirement requirement : requirements) {
            int remaining = requirement.quantity();
            int allocatedQuantity = 0;
            List<EmergencyResource> candidates = current.values().stream()
                    .filter(EmergencyResource::available)
                    .filter(item -> item.typeCode().equals(requirement.resourceTypeCode()))
                    .filter(item -> item.appliesTo(event.eventType()))
                    .sorted(candidateOrder(event.cityCode(), eventCenter, cityCenters))
                    .toList();
            for (EmergencyResource candidate : candidates) {
                if (remaining == 0) break;
                boolean local = candidate.cityCode().equals(event.cityCode());
                int dispatchable = local
                        ? candidate.availableQuantity()
                        : Math.max(0, candidate.availableQuantity()
                                - candidate.minimumReserveQuantity());
                int quantity = Math.min(remaining, dispatchable);
                if (quantity == 0) continue;
                double distance = local ? 0
                        : roundDistance(cityDistancePort.estimatedDistanceKm(
                                eventCenter, requireCenter(cityCenters, candidate.cityCode())));
                AllocatedResource snapshot = new AllocatedResource(
                        candidate.resourceId(), candidate.typeCode(), candidate.type(),
                        candidate.name(), candidate.cityCode(), candidate.city(),
                        quantity, candidate.unit(), requirement.purpose(), distance,
                        local ? DispatchScope.LOCAL : DispatchScope.CROSS_CITY
                );
                allocations.add(ResourceAllocation.reserved(
                        "RA-" + UUID.randomUUID(), workflowId, event.eventId(),
                        planId, planVersion, snapshot, now
                ));
                current.put(candidate.resourceId(), candidate.reserve(quantity));
                allocatedQuantity += quantity;
                remaining -= quantity;
            }
            if (remaining > 0) {
                shortages.add(new ResourceShortage(
                        requirement.resourceTypeCode(), requirement.resourceTypeName(),
                        requirement.quantity(), allocatedQuantity, remaining,
                        requirement.unit(), "同城及跨市可调度库存不足"
                ));
            }
        }

        List<EmergencyResource> changedOriginals = new ArrayList<>();
        List<EmergencyResource> changedUpdates = new ArrayList<>();
        for (Map.Entry<String, EmergencyResource> entry : current.entrySet()) {
            EmergencyResource original = originals.get(entry.getKey());
            if (original != null && original.lockVersion() != entry.getValue().lockVersion()) {
                changedOriginals.add(original);
                changedUpdates.add(entry.getValue());
            }
        }
        return new ResourceAllocationResult(
                List.copyOf(allocations), List.copyOf(changedOriginals),
                List.copyOf(changedUpdates), List.copyOf(shortages)
        );
    }

    private Comparator<EmergencyResource> candidateOrder(
            String eventCityCode, GeoPoint eventCenter, Map<String, GeoPoint> cityCenters
    ) {
        return Comparator
                .comparing((EmergencyResource item) -> !item.cityCode().equals(eventCityCode))
                .thenComparingDouble(item -> item.cityCode().equals(eventCityCode) ? 0
                        : cityDistancePort.estimatedDistanceKm(
                                eventCenter, requireCenter(cityCenters, item.cityCode())))
                .thenComparing(EmergencyResource::cityCode)
                .thenComparing(EmergencyResource::resourceId);
    }

    private GeoPoint requireCenter(Map<String, GeoPoint> cityCenters, String cityCode) {
        GeoPoint point = cityCenters == null ? null : cityCenters.get(cityCode);
        if (point == null) {
            throw new IllegalArgumentException("资源库缺少城市中心坐标：" + cityCode);
        }
        return point;
    }

    private double roundDistance(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
