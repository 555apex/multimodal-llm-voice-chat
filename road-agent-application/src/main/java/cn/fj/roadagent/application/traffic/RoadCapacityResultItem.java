package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.RoadCapacity;

/** 对外展示的路线通行能力评估行。 */
public record RoadCapacityResultItem(
        String routeCode,
        String routeName,
        double actualCapacityVph,
        double designCapacityVph,
        double utilizationRatio,
        String capacityLevel,
        String capacityLevelName
) {
    public static RoadCapacityResultItem from(RoadCapacity capacity) {
        return new RoadCapacityResultItem(
                capacity.routeCode(), capacity.routeName(), capacity.actualCapacityVph(),
                capacity.designCapacityVph(), capacity.utilizationRatio(),
                capacity.level().name(), capacity.level().displayName()
        );
    }
}
