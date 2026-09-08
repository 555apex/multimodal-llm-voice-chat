package cn.fj.roadagent.application.facility;

import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityHealthState;

import java.time.Instant;
import java.util.List;

public record FacilityFocusItem(
        String facilityName,
        FacilityHealthState healthState,
        AlarmLevel highestAlarmLevel,
        long activeAlertCount,
        long pendingCount,
        long confirmedCount,
        List<String> metricNames,
        Instant oldestTriggerTime,
        Instant latestCollectTime,
        String focusReason
) {
    public FacilityFocusItem {
        metricNames = List.copyOf(metricNames);
    }
}
