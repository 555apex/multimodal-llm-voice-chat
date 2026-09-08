package cn.fj.roadagent.application.port;

import cn.fj.roadagent.domain.dispatch.EventClassificationAttempt;

public interface EventClassificationLogPort {
    int nextAttemptNumber(String eventId);

    boolean insert(EventClassificationAttempt attempt);

    long countLatestFailuresForPendingEvents();
}
