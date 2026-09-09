package cn.fj.roadagent.domain.traffic;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CapacityLevelTest {

    @ParameterizedTest
    @CsvSource({
            "0, NORMAL",
            "0.1499, NORMAL",
            "0.15, BOTTLENECK",
            "0.2999, BOTTLENECK",
            "0.30, SEVERE_BOTTLENECK",
            "1.00, SEVERE_BOTTLENECK"
    })
    void appliesProjectThresholdBoundaries(double ratio, CapacityLevel expected) {
        assertEquals(expected, CapacityLevel.fromUtilization(ratio));
    }
}
