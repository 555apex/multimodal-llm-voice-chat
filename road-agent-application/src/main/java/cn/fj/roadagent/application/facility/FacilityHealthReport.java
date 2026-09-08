package cn.fj.roadagent.application.facility;

import cn.fj.roadagent.domain.facility.FacilityHealthState;

import java.time.Instant;
import java.util.List;

public record FacilityHealthReport(
        Instant generatedAt,
        Instant dataAsOf,
        long activeAlertCount,
        long affectedFacilityCount,
        long warningCount,
        long severeCount,
        long emergencyCount,
        long pendingCount,
        long confirmedCount,
        FacilityHealthState overallHealth,
        String summary,
        List<FacilityFocusItem> facilities
) {
    public FacilityHealthReport {
        facilities = List.copyOf(facilities);
    }
}
