package cn.fj.roadagent.application.facility;

import cn.fj.roadagent.domain.facility.FacilityAlertResolution;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;

public record FacilityAlertTransitionCommand(
        long alertId,
        FacilityAlertStatus expectedStatus,
        FacilityAlertStatus targetStatus,
        FacilityAlertResolution resolutionType,
        String remark
) {
}
