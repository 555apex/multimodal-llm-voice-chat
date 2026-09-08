package cn.fj.roadagent.application.facility;

import cn.fj.roadagent.domain.facility.FacilityAlert;

public interface TransitionFacilityAlertUseCase {
    FacilityAlert transition(FacilityAlertTransitionCommand command);
}
