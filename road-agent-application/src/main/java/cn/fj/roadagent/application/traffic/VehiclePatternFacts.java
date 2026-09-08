package cn.fj.roadagent.application.traffic;

import cn.fj.roadagent.domain.traffic.TrafficQueryType;

import java.time.Instant;
import java.util.List;

public record VehiclePatternFacts(
        TrafficQueryType queryType,
        String title,
        String analysisCity,
        List<VehicleStructureResultItem> structureRows,
        List<VehicleTimeFeatureResultItem> timeFeatureRows,
        List<VehicleDayTypeResultItem> dayTypeRows,
        List<HourlyVehicleFlowResultItem> hourlySeries,
        Instant acquiredAt,
        int missingHourCount
) {
    public VehiclePatternFacts {
        structureRows = structureRows == null ? List.of() : List.copyOf(structureRows);
        timeFeatureRows = timeFeatureRows == null ? List.of() : List.copyOf(timeFeatureRows);
        dayTypeRows = dayTypeRows == null ? List.of() : List.copyOf(dayTypeRows);
        hourlySeries = hourlySeries == null ? List.of() : List.copyOf(hourlySeries);
        if (missingHourCount < 0 || missingHourCount > 24) {
            throw new IllegalArgumentException("missingHourCount必须介于0到24");
        }
    }

    public VehiclePatternFacts(
            TrafficQueryType queryType,
            String title,
            String analysisCity,
            List<VehicleStructureResultItem> structureRows,
            List<VehicleTimeFeatureResultItem> timeFeatureRows,
            List<VehicleDayTypeResultItem> dayTypeRows,
            List<HourlyVehicleFlowResultItem> hourlySeries,
            Instant acquiredAt
    ) {
        this(queryType, title, analysisCity, structureRows, timeFeatureRows,
                dayTypeRows, hourlySeries, acquiredAt, 0);
    }
}
