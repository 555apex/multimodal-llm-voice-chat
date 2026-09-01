package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrafficStatusTest {

    @Test
    void mapsAllFiveDatabaseCodesWithoutRecalculation() {
        assertEquals(TrafficStatus.SMOOTH, TrafficStatus.fromDatabase("10"));
        assertEquals(TrafficStatus.LIGHT_CONGESTION, TrafficStatus.fromDatabase("20"));
        assertEquals(TrafficStatus.MODERATE_CONGESTION, TrafficStatus.fromDatabase("30"));
        assertEquals(TrafficStatus.SEVERE_CONGESTION, TrafficStatus.fromDatabase("40"));
        assertEquals(TrafficStatus.BLOCKED, TrafficStatus.fromDatabase("50"));
        assertFalse(TrafficStatus.SMOOTH.abnormal());
        assertTrue(TrafficStatus.LIGHT_CONGESTION.abnormal());
    }

    @Test
    void rejectsNullAndIllegalDatabaseStatus() {
        assertThrows(IllegalArgumentException.class, () -> TrafficStatus.fromDatabase(null));
        assertThrows(IllegalArgumentException.class, () -> TrafficStatus.fromDatabase("25"));
    }
}
