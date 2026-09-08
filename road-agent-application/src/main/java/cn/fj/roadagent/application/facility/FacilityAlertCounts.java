package cn.fj.roadagent.application.facility;

import java.time.Instant;

public record FacilityAlertCounts(
        long pending,
        long confirmed,
        long closed,
        long activeWarning,
        long activeSevere,
        long activeEmergency,
        Instant dataAsOf
) {
}
