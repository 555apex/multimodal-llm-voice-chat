package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrafficQueryTest {

    @Test
    void shouldNormalizeInput() {
        TrafficQuery query = new TrafficQuery(" 350100 ", " 五四路 ", " 南向北 ");

        assertEquals("350100", query.areaCode());
        assertEquals("五四路", query.roadName());
        assertEquals("南向北", query.direction());
    }

    @Test
    void shouldRejectInvalidAreaCode() {
        assertThrows(InvalidTrafficQueryException.class,
                () -> new TrafficQuery("福州", "五四路", null));
    }
}
