package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.AbnormalEventPort;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.EventClassificationLogPort;
import cn.fj.roadagent.application.port.UnitOfWork;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EventClassificationAttempt;
import cn.fj.roadagent.domain.dispatch.EventClassificationMethod;
import cn.fj.roadagent.domain.dispatch.UnclassifiedEmergencyEvent;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmergencyEventClassificationServiceTest {
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-09-04T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldUseRuleBeforeModelAndPersistEvidence() {
        EventPort events = new EventPort(new UnclassifiedEmergencyEvent(
                "CNO-1", CLOCK.instant(), "高速路两车追尾发生交通事故",
                null, null, "福州市闽侯县", "G70", "福银高速"));
        LogPort logs = new LogPort();
        Model model = new Model(new EventClassificationProposal("DT01", 0.2, "不应调用"));
        service(events, logs, model).classifyNext();

        assertEquals("ET106", events.assignedType);
        assertEquals(0, model.calls);
        assertEquals(EventClassificationMethod.RULE, logs.values.get(0).method());
    }

    @Test
    void shouldAcceptLowConfidenceModelFallback() {
        EventPort events = new EventPort(new UnclassifiedEmergencyEvent(
                "CNO-2", CLOCK.instant(), "前方交通异常，请核查",
                null, null, "泉州市南安市", "G72", "泉南高速"));
        LogPort logs = new LogPort();
        Model model = new Model(new EventClassificationProposal("ET109", 0.31, "描述更接近路障"));
        service(events, logs, model).classifyNext();

        assertEquals("ET109", events.assignedType);
        assertEquals(1, model.calls);
        assertEquals(0.31, logs.values.get(0).confidence());
    }

    @Test
    void shouldImmediatelyRetryTheOldestUnclassifiedEvent() {
        EventPort events = new EventPort(new UnclassifiedEmergencyEvent(
                "CNO-3", CLOCK.instant(), "高速路发生团雾",
                null, null, "宁德市霞浦县", "G15", "沈海高速"));
        LogPort logs = new LogPort();

        assertTrue(service(events, logs, new Model(null)).retryNext());

        assertEquals("ET108", events.assignedType);
        assertEquals(EventClassificationMethod.RULE, logs.values.get(0).method());
    }

    private EmergencyEventClassificationService service(
            EventPort events, LogPort logs, Model model) {
        UnitOfWork direct = new UnitOfWork() {
            @Override public <T> T required(Supplier<T> operation) { return operation.get(); }
        };
        return new EmergencyEventClassificationService(
                events, logs, model, direct, CLOCK, Duration.ofSeconds(30), "test-model");
    }

    private static final class EventPort implements AbnormalEventPort {
        private final UnclassifiedEmergencyEvent event;
        private String assignedType;
        private EventPort(UnclassifiedEmergencyEvent event) { this.event = event; }
        @Override public Optional<UnclassifiedEmergencyEvent> findNextUnclassified(Instant retryBefore) {
            return assignedType == null ? Optional.of(event) : Optional.empty();
        }
        @Override public Optional<UnclassifiedEmergencyEvent> findUnclassifiedById(String eventId) {
            return event.eventId().equals(eventId) && assignedType == null ? Optional.of(event) : Optional.empty();
        }
        @Override public boolean assignEventTypeIfAbsent(String eventId, String eventType) {
            if (assignedType != null) return false;
            assignedType = eventType;
            return true;
        }
        @Override public Optional<EmergencyEvent> findPendingById(String eventId) { return Optional.empty(); }
        @Override public Optional<EmergencyEvent> lockPendingById(String eventId) { return Optional.empty(); }
        @Override public Optional<EmergencyEvent> findNextPending() { return Optional.empty(); }
        @Override public long countPending() { return 0; }
        @Override public boolean markDispatchApproved(String eventId, Instant updateTime) { return false; }
        @Override public boolean markNoDispatch(String eventId, String reason, Instant updateTime) { return false; }
    }

    private static final class LogPort implements EventClassificationLogPort {
        private final List<EventClassificationAttempt> values = new ArrayList<>();
        @Override public int nextAttemptNumber(String eventId) { return values.size() + 1; }
        @Override public boolean insert(EventClassificationAttempt attempt) { return values.add(attempt); }
        @Override public long countLatestFailuresForPendingEvents() { return 0; }
    }

    private static final class Model implements ChatModelPort {
        private final EventClassificationProposal result;
        private int calls;
        private Model(EventClassificationProposal result) { this.result = result; }
        @Override public ModelResponse generate(ModelRequest request) { throw new UnsupportedOperationException(); }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            calls++;
            return resultType.cast(result);
        }
        @Override public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
