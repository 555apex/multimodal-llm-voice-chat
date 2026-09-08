package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import java.time.Instant;
import java.util.List;

public record OdTrafficFacts(
        TrafficQueryType queryType, String title, List<SelectedRegionResultItem> selectedRegions,
        List<OdDestinationTendencyResultItem> destinationRows,
        List<OdMatrixRowResultItem> matrixRows,
        Instant acquiredAt, List<String> warnings
) {
    public OdTrafficFacts {
        selectedRegions = List.copyOf(selectedRegions);
        destinationRows = List.copyOf(destinationRows);
        matrixRows = List.copyOf(matrixRows);
        warnings = List.copyOf(warnings);
    }
}
