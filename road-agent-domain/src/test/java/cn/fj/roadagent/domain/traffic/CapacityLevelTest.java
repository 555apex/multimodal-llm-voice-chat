package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapacityLevelTest {

    @ParameterizedTest
    @CsvSource({
            "0, NORMAL",
            "0.15, NORMAL",
            "0.20, NORMAL",
            "0.2001, BOTTLENECK",
            "0.30, BOTTLENECK",
            "0.3001, SEVERE_BOTTLENECK",
            "1.00, SEVERE_BOTTLENECK",
            "1.2792, SEVERE_BOTTLENECK"
    })
    void appliesProjectThresholdBoundaries(double ratio, CapacityLevel expected) {
        assertEquals(expected, CapacityLevel.fromUtilization(ratio));
    }
}
