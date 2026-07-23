package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FujianCityTest {

    @Test
    void shouldMapCityNameToTrustedAdcode() {
        assertEquals("350100", FujianCity.fromName("福建省福州市").orElseThrow().adcode());
        assertEquals("350500", FujianCity.fromName("泉州").orElseThrow().adcode());
        assertTrue(FujianCity.fromName("杭州").isEmpty());
    }
}
