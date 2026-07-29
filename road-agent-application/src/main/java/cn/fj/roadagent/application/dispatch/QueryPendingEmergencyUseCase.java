package cn.fj.roadagent.application.dispatch;

import java.util.Optional;

public interface QueryPendingEmergencyUseCase {
    Optional<EmergencyAlert> nextPending();
}
