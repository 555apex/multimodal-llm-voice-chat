package cn.fj.roadagent.adapters.traffic.mock;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.domain.traffic.TrafficQuery;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockTrafficDataAdapterTest {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-17T08:00:00Z"), ZoneOffset.UTC
    );

    @Test
    void shouldReturnNormalScenario() {
        MockTrafficDataAdapter adapter = new MockTrafficDataAdapter(MockTrafficScenario.NORMAL, CLOCK);

        var snapshot = adapter.query(new TrafficQuery("350100", "五四路", null));

        assertEquals("MOCK", snapshot.source());
        assertTrue(snapshot.mock());
        assertEquals(2, snapshot.segments().size());
    }

    @Test
    void shouldSimulateServerError() {
        MockTrafficDataAdapter adapter = new MockTrafficDataAdapter(MockTrafficScenario.SERVER_ERROR, CLOCK);

        assertThrows(ExternalServiceException.class,
                () -> adapter.query(new TrafficQuery("350100", "五四路", null)));
    }
}
