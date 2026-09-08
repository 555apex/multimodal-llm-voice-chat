package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

/** Agent 与 REST 共同使用的统一 MySQL 交通结果。 */
public record HighwayTrafficResult(
        TrafficQueryType queryType, String title, String summary,
        List<RouteTrafficResultItem> routeSummaries,
        List<HighwayTrafficSegmentResultItem> segments,
        List<RoadCapacityResultItem> capacityRows,
        List<SelectedRegionResultItem> selectedRegions,
        List<RegionalPairResultItem> regionalPairRows,
        List<RegionalChannelResultItem> regionalChannelRows,
        int totalRegionalPairCount, int totalRegionalChannelCount,
        String analysisCity,
        List<VehicleStructureResultItem> vehicleStructureRows,
        List<VehicleTimeFeatureResultItem> vehicleTimeFeatureRows,
        List<VehicleDayTypeResultItem> vehicleDayTypeRows,
        List<HourlyVehicleFlowResultItem> hourlyVehicleSeries,
        int totalSegmentCount, int displayedSegmentCount, boolean truncated,
        String source, Instant acquiredAt, List<String> warnings, String traceId,
        List<OdDestinationTendencyResultItem> odDestinationRows,
        List<OdMatrixRowResultItem> odMatrixRows
) {
    public HighwayTrafficResult {
        routeSummaries = copy(routeSummaries); segments = copy(segments); capacityRows = copy(capacityRows);
        selectedRegions = copy(selectedRegions); regionalPairRows = copy(regionalPairRows);
        regionalChannelRows = copy(regionalChannelRows);
        vehicleStructureRows = copy(vehicleStructureRows); vehicleTimeFeatureRows = copy(vehicleTimeFeatureRows);
        vehicleDayTypeRows = copy(vehicleDayTypeRows); hourlyVehicleSeries = copy(hourlyVehicleSeries);
        warnings = copy(warnings); odDestinationRows = copy(odDestinationRows); odMatrixRows = copy(odMatrixRows);
    }

    private static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }

    /** 兼容既有路况、容量测试和调用方。 */
    public HighwayTrafficResult(TrafficQueryType queryType, String title, String summary,
            List<RouteTrafficResultItem> routeSummaries, List<HighwayTrafficSegmentResultItem> segments,
            List<RoadCapacityResultItem> capacityRows, int totalSegmentCount, int displayedSegmentCount,
            boolean truncated, String source, Instant acquiredAt, List<String> warnings, String traceId) {
        this(queryType, title, summary, routeSummaries, segments, capacityRows,
                List.of(), List.of(), List.of(), 0, 0, null,
                List.of(), List.of(), List.of(), List.of(), totalSegmentCount, displayedSegmentCount,
                truncated, source, acquiredAt, warnings, traceId, List.of(), List.of());
    }

    public static HighwayTrafficResult fromFacts(HighwayTrafficFacts facts, String summary, String traceId) {
        return new HighwayTrafficResult(facts.queryType(), facts.title(), summary,
                facts.routeSummaries().stream().map(RouteTrafficResultItem::from).toList(),
                facts.segments().stream().map(HighwayTrafficSegmentResultItem::from).toList(),
                List.of(), facts.totalSegmentCount(), facts.segments().size(), facts.truncated(),
                "MYSQL", facts.acquiredAt(), facts.warnings(), traceId);
    }

    public static HighwayTrafficResult fromCapacityFacts(RoadCapacityFacts facts, String summary, String traceId) {
        return new HighwayTrafficResult(facts.queryType(), facts.title(), summary, List.of(), List.of(),
                facts.rows().stream().map(RoadCapacityResultItem::from).toList(), facts.totalCount(), facts.rows().size(),
                facts.truncated(), "MYSQL", facts.acquiredAt(), facts.warnings(), traceId);
    }

    public static HighwayTrafficResult fromRegionalFacts(RegionalTrafficFacts facts, String summary, String traceId) {
        int total = switch (facts.queryType()) {
            case REGIONAL_PAIR_PRESSURE -> facts.totalPairCount();
            case REGIONAL_KEY_CHANNELS -> facts.totalChannelCount();
            default -> facts.totalPairCount() + facts.totalChannelCount();
        };
        int displayed = switch (facts.queryType()) {
            case REGIONAL_PAIR_PRESSURE -> facts.pairRows().size();
            case REGIONAL_KEY_CHANNELS -> facts.channelRows().size();
            default -> facts.pairRows().size() + facts.channelRows().size();
        };
        return new HighwayTrafficResult(facts.queryType(), facts.title(), summary,
                List.of(), List.of(), List.of(), facts.selectedRegions(), facts.pairRows(), facts.channelRows(),
                facts.totalPairCount(), facts.totalChannelCount(), null,
                List.of(), List.of(), List.of(), List.of(), total, displayed, total > displayed,
                "MYSQL", facts.acquiredAt(), facts.warnings(), traceId, List.of(), List.of());
    }

    public static HighwayTrafficResult fromVehicleFacts(VehiclePatternFacts facts, String summary, String traceId) {
        List<String> warnings = facts.missingHourCount() > 0 && !facts.hourlySeries().isEmpty()
                ? List.of("有" + facts.missingHourCount() + "个小时源数据缺失，图表按项目规则补0；补0不代表实际无车。") : List.of();
        return new HighwayTrafficResult(facts.queryType(), facts.title(), summary,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0, 0,
                facts.analysisCity(), facts.structureRows(), facts.timeFeatureRows(), facts.dayTypeRows(),
                facts.hourlySeries(), 0, 0, false, "MYSQL", facts.acquiredAt(), warnings, traceId,
                List.of(), List.of());
    }

    public static HighwayTrafficResult fromOdFacts(OdTrafficFacts facts, String summary, String traceId) {
        return new HighwayTrafficResult(facts.queryType(), facts.title(), summary,
                List.of(), List.of(), List.of(), facts.selectedRegions(), List.of(), List.of(), 0, 0,
                null, List.of(), List.of(), List.of(), List.of(), 0, 0, false, "MYSQL", facts.acquiredAt(),
                facts.warnings(), traceId, facts.destinationRows(), facts.matrixRows());
    }
}
