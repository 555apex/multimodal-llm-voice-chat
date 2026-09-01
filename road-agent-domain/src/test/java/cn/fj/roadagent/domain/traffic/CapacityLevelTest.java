package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapacityLevelTest {

    @ParameterizedTest
    @CsvSource({
            "0, SEVERE_BOTTLENECK",
            "0.30, SEVERE_BOTTLENECK",
            "0.3001, BOTTLENECK",
            "0.7999, BOTTLENECK",
            "0.80, NORMAL",
            "1.00, NORMAL"
    })
    void appliesProjectThresholdBoundaries(double ratio, CapacityLevel expected) {
        assertEquals(expected, CapacityLevel.fromUtilization(ratio));
    }
}
