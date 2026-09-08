package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

public record RegionalTrafficFacts(
        TrafficQueryType queryType,
        String title,
        List<SelectedRegionResultItem> selectedRegions,
        List<RegionalPairResultItem> pairRows,
        List<RegionalChannelResultItem> channelRows,
        int totalPairCount,
        int totalChannelCount,
        Instant acquiredAt,
        List<String> warnings
) {
    public RegionalTrafficFacts {
        selectedRegions = selectedRegions == null ? List.of() : List.copyOf(selectedRegions);
        pairRows = pairRows == null ? List.of() : List.copyOf(pairRows);
        channelRows = channelRows == null ? List.of() : List.copyOf(channelRows);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

}
