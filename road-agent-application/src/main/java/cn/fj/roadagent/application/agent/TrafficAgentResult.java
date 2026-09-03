package cn.fj.roadagent.application.agent;

import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.OdCityFlowResultItem;
import cn.fj.roadagent.application.traffic.OdChannelResultItem;
import cn.fj.roadagent.application.traffic.HighwayTrafficSegmentResultItem;
import cn.fj.roadagent.application.traffic.RouteTrafficResultItem;
import cn.fj.roadagent.application.traffic.RoadCapacityResultItem;
import cn.fj.roadagent.application.traffic.SelectedRegionResultItem;
import cn.fj.roadagent.application.traffic.TransportHubResultItem;
import cn.fj.roadagent.application.traffic.RegionPressureResultItem;
import cn.fj.roadagent.application.traffic.RoutePressureResultItem;
import cn.fj.roadagent.application.traffic.VehicleStructureResultItem;
import cn.fj.roadagent.application.traffic.VehicleTimeFeatureResultItem;
import cn.fj.roadagent.application.traffic.VehicleDayTypeResultItem;
import cn.fj.roadagent.application.traffic.HourlyVehicleFlowResultItem;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

/** 对话事件使用的扁平交通结果，避免前端了解内部TrafficQuery对象。 */
public record TrafficAgentResult(
        TrafficQueryType queryType,
        String title,
        String summary,
        List<RouteTrafficResultItem> routeSummaries,
        List<HighwayTrafficSegmentResultItem> segments,
        List<RoadCapacityResultItem> capacityRows,
        List<SelectedRegionResultItem> selectedRegions,
        List<TransportHubResultItem> hubRows,
        List<RegionPressureResultItem> regionPressureRows,
        List<RoutePressureResultItem> routePressureRows,
        String analysisCity,
        List<VehicleStructureResultItem> vehicleStructureRows,
        List<VehicleTimeFeatureResultItem> vehicleTimeFeatureRows,
        List<VehicleDayTypeResultItem> vehicleDayTypeRows,
        List<HourlyVehicleFlowResultItem> hourlyVehicleSeries,
        int totalSegmentCount,
        int displayedSegmentCount,
        boolean truncated,
        String source,
        Instant acquiredAt,
        List<String> warnings,
        String traceId,
        List<OdCityFlowResultItem> odCityFlowRows,
        List<OdChannelResultItem> odChannelRows,
        Integer periodDays,
        List<SelectedRegionResultItem> missingRegions
) {
    public static TrafficAgentResult from(HighwayTrafficResult result) {
        return new TrafficAgentResult(
                result.queryType(), result.title(), result.summary(), result.routeSummaries(),
                result.segments(), result.capacityRows(), result.selectedRegions(), result.hubRows(),
                result.regionPressureRows(), result.routePressureRows(), result.analysisCity(),
                result.vehicleStructureRows(), result.vehicleTimeFeatureRows(), result.vehicleDayTypeRows(),
                result.hourlyVehicleSeries(), result.totalSegmentCount(), result.displayedSegmentCount(),
                result.truncated(), result.source(), result.acquiredAt(), result.warnings(), result.traceId(),
                result.odCityFlowRows(), result.odChannelRows(), result.periodDays(), result.missingRegions()
        );
    }
}
