package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

/** Agent 与 REST 共同使用的统一 MySQL 交通结果。 */
public record HighwayTrafficResult(
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
    public HighwayTrafficResult {
        routeSummaries = routeSummaries == null ? List.of() : List.copyOf(routeSummaries);
        segments = segments == null ? List.of() : List.copyOf(segments);
        capacityRows = capacityRows == null ? List.of() : List.copyOf(capacityRows);
        selectedRegions = selectedRegions == null ? List.of() : List.copyOf(selectedRegions);
        hubRows = hubRows == null ? List.of() : List.copyOf(hubRows);
        regionPressureRows = regionPressureRows == null ? List.of() : List.copyOf(regionPressureRows);
        routePressureRows = routePressureRows == null ? List.of() : List.copyOf(routePressureRows);
        vehicleStructureRows = vehicleStructureRows == null ? List.of() : List.copyOf(vehicleStructureRows);
        vehicleTimeFeatureRows = vehicleTimeFeatureRows == null ? List.of() : List.copyOf(vehicleTimeFeatureRows);
        vehicleDayTypeRows = vehicleDayTypeRows == null ? List.of() : List.copyOf(vehicleDayTypeRows);
        hourlyVehicleSeries = hourlyVehicleSeries == null ? List.of() : List.copyOf(hourlyVehicleSeries);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        odCityFlowRows = odCityFlowRows == null ? List.of() : List.copyOf(odCityFlowRows);
        odChannelRows = odChannelRows == null ? List.of() : List.copyOf(odChannelRows);
        missingRegions = missingRegions == null ? List.of() : List.copyOf(missingRegions);
    }

    /** 保留既有全部业务的构造契约。 */
    public HighwayTrafficResult(TrafficQueryType queryType, String title, String summary,
            List<RouteTrafficResultItem> routeSummaries, List<HighwayTrafficSegmentResultItem> segments,
            List<RoadCapacityResultItem> capacityRows, List<SelectedRegionResultItem> selectedRegions,
            List<TransportHubResultItem> hubRows, List<RegionPressureResultItem> regionPressureRows,
            List<RoutePressureResultItem> routePressureRows, String analysisCity,
            List<VehicleStructureResultItem> vehicleStructureRows, List<VehicleTimeFeatureResultItem> vehicleTimeFeatureRows,
            List<VehicleDayTypeResultItem> vehicleDayTypeRows, List<HourlyVehicleFlowResultItem> hourlyVehicleSeries,
            int totalSegmentCount, int displayedSegmentCount, boolean truncated,
            String source, Instant acquiredAt, List<String> warnings, String traceId) {
        this(queryType, title, summary, routeSummaries, segments, capacityRows, selectedRegions, hubRows,
                regionPressureRows, routePressureRows, analysisCity, vehicleStructureRows, vehicleTimeFeatureRows,
                vehicleDayTypeRows, hourlyVehicleSeries, totalSegmentCount, displayedSegmentCount, truncated,
                source, acquiredAt, warnings, traceId, List.of(), List.of(), null, List.of());
    }

    public static HighwayTrafficResult fromOdFacts(OdTrafficFacts facts, String summary, String traceId) {
        return new HighwayTrafficResult(facts.queryType(), facts.title(), summary,
                List.of(), List.of(), List.of(), facts.selectedRegions(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(), 0, 0, false, "MYSQL", facts.acquiredAt(),
                facts.warnings(), traceId, facts.cityRows(), facts.channelRows(), 7, facts.missingRegions());
    }

    /** 兼容既有路况、容量测试和调用方。 */
    public HighwayTrafficResult(
            TrafficQueryType queryType,
            String title,
            String summary,
            List<RouteTrafficResultItem> routeSummaries,
            List<HighwayTrafficSegmentResultItem> segments,
            List<RoadCapacityResultItem> capacityRows,
            int totalSegmentCount,
            int displayedSegmentCount,
            boolean truncated,
            String source,
            Instant acquiredAt,
            List<String> warnings,
            String traceId
    ) {
        this(queryType, title, summary, routeSummaries, segments, capacityRows,
                List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(),
                totalSegmentCount, displayedSegmentCount, truncated, source, acquiredAt, warnings, traceId);
    }

    public static HighwayTrafficResult fromFacts(
            HighwayTrafficFacts facts,
            String summary,
            String traceId
    ) {
        return new HighwayTrafficResult(
                facts.queryType(), facts.title(), summary,
                facts.routeSummaries().stream().map(RouteTrafficResultItem::from).toList(),
                facts.segments().stream().map(HighwayTrafficSegmentResultItem::from).toList(),
                List.of(), List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(),
                facts.totalSegmentCount(), facts.segments().size(), facts.truncated(), "MYSQL",
                facts.acquiredAt(), facts.warnings(), traceId
        );
    }

    public static HighwayTrafficResult fromCapacityFacts(
            RoadCapacityFacts facts,
            String summary,
            String traceId
    ) {
        return new HighwayTrafficResult(
                facts.queryType(), facts.title(), summary, List.of(), List.of(),
                facts.rows().stream().map(RoadCapacityResultItem::from).toList(),
                List.of(), List.of(), List.of(), List.of(), null,
                List.of(), List.of(), List.of(), List.of(),
                facts.totalCount(), facts.rows().size(), facts.truncated(), "MYSQL",
                facts.acquiredAt(), facts.warnings(), traceId
        );
    }

    public static HighwayTrafficResult fromRegionalFacts(
            RegionalTrafficFacts facts,
            String summary,
            List<RegionPressureResultItem> interpretedRegions,
            String traceId
    ) {
        int total = switch (facts.queryType()) {
            case CHECKPOINT_PRESSURE -> facts.totalHubCount();
            case CITY_PRESSURE -> facts.totalRegionCount();
            case ROUTE_PRESSURE -> facts.totalRouteCount();
            default -> facts.totalHubCount();
        };
        int displayed = switch (facts.queryType()) {
            case CHECKPOINT_PRESSURE -> facts.hubRows().size();
            case CITY_PRESSURE -> interpretedRegions.size();
            case ROUTE_PRESSURE -> facts.routeRows().size();
            default -> facts.hubRows().size();
        };
        return new HighwayTrafficResult(
                facts.queryType(), facts.title(), summary, List.of(), List.of(), List.of(),
                facts.selectedRegions(), facts.hubRows(), interpretedRegions, facts.routeRows(), null,
                List.of(), List.of(), List.of(), List.of(),
                total, displayed, total > displayed, "MYSQL", facts.acquiredAt(), List.of(), traceId
        );
    }

    public static HighwayTrafficResult fromVehicleFacts(
            VehiclePatternFacts facts,
            String summary,
            String traceId
    ) {
        return new HighwayTrafficResult(
                facts.queryType(), facts.title(), summary, List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), facts.analysisCity(),
                facts.structureRows(), facts.timeFeatureRows(), facts.dayTypeRows(), facts.hourlySeries(),
                0, 0, false, "MYSQL", facts.acquiredAt(), List.of(), traceId
        );
    }
}
