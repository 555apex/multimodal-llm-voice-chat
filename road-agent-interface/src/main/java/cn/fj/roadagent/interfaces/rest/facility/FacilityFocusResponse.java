package cn.fj.roadagent.interfaces.rest.facility;

import cn.fj.roadagent.application.facility.FacilityFocusItem;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityHealthState;

import java.time.Instant;
import java.util.List;

public record FacilityFocusResponse(
        String facilityName,
        FacilityHealthState healthState, String healthStateName,
        AlarmLevel highestAlarmLevel, String highestAlarmLevelName,
        long activeAlertCount, long pendingCount, long confirmedCount,
        List<String> metricNames, Instant oldestTriggerTime, Instant latestCollectTime,
        String focusReason
) {
    public static FacilityFocusResponse from(FacilityFocusItem item) {
        return new FacilityFocusResponse(
                item.facilityName(), item.healthState(), item.healthState().displayName(),
                item.highestAlarmLevel(), item.highestAlarmLevel().displayName(),
                item.activeAlertCount(), item.pendingCount(), item.confirmedCount(),
                item.metricNames(), item.oldestTriggerTime(), item.latestCollectTime(),
                item.focusReason()
        );
    }
}
