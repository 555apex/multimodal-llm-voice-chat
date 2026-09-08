package cn.fj.roadagent.interfaces.rest.traffic;

import cn.fj.roadagent.application.traffic.HighwayTrafficResult;
import cn.fj.roadagent.application.traffic.OdDestinationTendencyResultItem;
import cn.fj.roadagent.application.traffic.OdMatrixRowResultItem;
import cn.fj.roadagent.application.traffic.HighwayTrafficSegmentResultItem;
import cn.fj.roadagent.application.traffic.RouteTrafficResultItem;
import cn.fj.roadagent.application.traffic.RoadCapacityResultItem;
import cn.fj.roadagent.application.traffic.SelectedRegionResultItem;
import cn.fj.roadagent.application.traffic.RegionalPairResultItem;
import cn.fj.roadagent.application.traffic.RegionalChannelResultItem;
import cn.fj.roadagent.application.traffic.VehicleStructureResultItem;
import cn.fj.roadagent.application.traffic.VehicleTimeFeatureResultItem;
import cn.fj.roadagent.application.traffic.VehicleDayTypeResultItem;
import cn.fj.roadagent.application.traffic.HourlyVehicleFlowResultItem;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

public record TrafficQueryResponse(
        TrafficQueryType queryType,
        String title,
        String summary,
        List<RouteTrafficResultItem> routeSummaries,
        List<HighwayTrafficSegmentResultItem> segments,
        List<RoadCapacityResultItem> capacityRows,
        List<SelectedRegionResultItem> selectedRegions,
        List<RegionalPairResultItem> regionalPairRows,
        List<RegionalChannelResultItem> regionalChannelRows,
        int totalRegionalPairCount,
        int totalRegionalChannelCount,
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
        List<OdDestinationTendencyResultItem> odDestinationRows,
        List<OdMatrixRowResultItem> odMatrixRows
) {
    public static TrafficQueryResponse from(HighwayTrafficResult result) {
        return new TrafficQueryResponse(
                result.queryType(), result.title(), result.summary(), result.routeSummaries(),
                result.segments(), result.capacityRows(), result.selectedRegions(), result.regionalPairRows(),
                result.regionalChannelRows(), result.totalRegionalPairCount(),
                result.totalRegionalChannelCount(), result.analysisCity(),
                result.vehicleStructureRows(), result.vehicleTimeFeatureRows(), result.vehicleDayTypeRows(),
                result.hourlyVehicleSeries(), result.totalSegmentCount(), result.displayedSegmentCount(),
                result.truncated(), result.source(), result.acquiredAt(), result.warnings(), result.traceId(),
                result.odDestinationRows(), result.odMatrixRows()
        );
    }
}
