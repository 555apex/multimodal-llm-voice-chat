package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.facility.FacilityAlertCounts;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlert;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;

import java.util.List;
import java.util.Optional;

public interface FacilityAlertPort {
    List<FacilityAlert> findPage(
            FacilityAlertStatus status, AlarmLevel alarmLevel, int offset, int limit
    );

    long count(FacilityAlertStatus status, AlarmLevel alarmLevel);

    FacilityAlertCounts counts();

    List<FacilityAlert> findActive();

    Optional<FacilityAlert> findById(long alertId);

    boolean updateStatus(
            long alertId,
            FacilityAlertStatus expectedStatus,
            FacilityAlertStatus targetStatus,
            String remark
    );
}
