package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import java.time.Instant;
import java.util.List;

public record OdTrafficFacts(
        TrafficQueryType queryType, String title, List<SelectedRegionResultItem> selectedRegions,
        List<SelectedRegionResultItem> missingRegions,
        List<OdCityFlowResultItem> cityRows, List<OdChannelResultItem> channelRows,
        int checkpointCount, long weeklyTotalFlow, long dailyAverageFlow,
        Instant acquiredAt, List<String> warnings
) {
    public OdTrafficFacts {
        selectedRegions = List.copyOf(selectedRegions);
        missingRegions = List.copyOf(missingRegions);
        cityRows = List.copyOf(cityRows);
        channelRows = List.copyOf(channelRows);
        warnings = List.copyOf(warnings);
    }
}
