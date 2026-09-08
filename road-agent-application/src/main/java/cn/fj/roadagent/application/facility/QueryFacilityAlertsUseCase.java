package cn.fj.roadagent.application.facility;

import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;

import java.util.List;

public interface QueryFacilityAlertsUseCase {
    FacilityAlertPage query(FacilityAlertStatus status, AlarmLevel alarmLevel, int page, int size);

    FacilityHealthReport healthReport();

    List<FacilityFocusItem> focus(int limit);
}
