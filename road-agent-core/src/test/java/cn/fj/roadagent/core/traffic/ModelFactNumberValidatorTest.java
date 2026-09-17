package cn.fj.roadagent.core.traffic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ModelFactNumberValidatorTest {
    @Test void acceptsExistingNumbersAndPercentages() {
        assertDoesNotThrow(() -> ModelFactNumberValidator.validate(
                "G104沿线日总流量为4,651，枢纽占比25%，均速38.5。",
                "日总流量=4651|枢纽占比=25.00%|均速=38.50|路线=G104"));
    }
    @Test void permitsOnlyNamedRatioConversions() {
        assertDoesNotThrow(() -> ModelFactNumberValidator.validate("小型客车占69.52%。", "shareRatio=0.6952"));
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("占比69.52%。", "speed=0.6952"));
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("占比80%。", "shareRatio=0.6952"));
    }
    @Test void acceptsExplicitDisplayRoundingAndQuantityUnits() {
        assertDoesNotThrow(() -> ModelFactNumberValidator.validate("总量12.35万，均速38.51。", "总量=123456|均速=38.506"));
        assertDoesNotThrow(() -> ModelFactNumberValidator.validate("总量1.23亿。", "总量=123456789"));
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("总量15.35万。", "总量=123456"));
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("总量123457。", "总量=123456"));
    }
    @Test void doesNotTreatPartialRouteDigitsAsFacts() {
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("有4条路线。", "路线=G104"));
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("G999畅通。", "路线=G104"));
    }
    @Test void rejectsInventedNumbers() {
        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate("流量将达到5000。", "流量=4651"));
    }
}
