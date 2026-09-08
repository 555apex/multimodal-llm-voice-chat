package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.EmergencyResponsePlan;

import java.util.Optional;

/** 已发布应急预案的只读端口。 */
public interface EmergencyResponsePlanPort {
    Optional<EmergencyResponsePlan> findActiveByEventType(String eventType);
}
