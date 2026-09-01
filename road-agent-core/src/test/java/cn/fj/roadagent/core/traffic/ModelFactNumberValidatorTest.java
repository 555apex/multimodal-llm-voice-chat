package cn.fj.roadagent.core.traffic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ModelFactNumberValidatorTest {

    @Test
    void acceptsNumbersAndPercentagesAlreadyPresentInFacts() {
        String facts = "日总流量=4651|枢纽占比=25.00%|均速=38.50|路线=G104";

        assertDoesNotThrow(() -> ModelFactNumberValidator.validate(
                "G104沿线日总流量为4,651，枢纽占比25%，均速38.5。",
                facts
        ));
    }

    @Test
    void rejectsNumbersInventedByModel() {
        String facts = "日总流量=4651|枢纽占比=25.00%";

        assertThrows(IllegalArgumentException.class, () -> ModelFactNumberValidator.validate(
                "预计日总流量将达到5000。",
                facts
        ));
    }
}
