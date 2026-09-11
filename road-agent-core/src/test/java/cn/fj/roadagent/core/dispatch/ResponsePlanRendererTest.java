package cn.fj.roadagent.core.dispatch;

import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResponsePlanSnapshot;
import cn.fj.roadagent.domain.dispatch.ResponsePlanResourceBaseline;
import cn.fj.roadagent.domain.dispatch.ResponsePlanResourceMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsePlanRendererTest {
    private final ResponsePlanRenderer renderer = new ResponsePlanRenderer();

    @Test
    void conciseTemplatesCoverSixteenTypesAndKeepNaturalLengthAndPunctuation() throws Exception {
        try (var stream = getClass().getResourceAsStream("/dispatch/compact-response-plans.txt")) {
            var lines = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
            assertEquals(16, lines.size());
            for (String line : lines) {
                String[] fields = line.split("\t", 2);
                var original = plan();
                var compact = new EmergencyResponsePlanSnapshot(original.planId(), fields[0], fields[0], 2,
                        original.requiredFacts(), fields[1].replace("\\n", "\n"),
                        original.resourceBaseline(), original.contentHash());
                for (Map<String, String> facts : List.of(Map.<String, String>of(),
                        Map.of("现场事实简述", "G205线K2196+226处受影响路段长1.5公里。"))) {
                    String result = renderer.render(compact, event(), facts, "");
                    int length = result.replaceAll("\\s", "").length();
                    assertTrue(length >= 300 && length <= 400, fields[0] + ":" + length);
                    assertTrue(!result.matches("(?s).*[，。；：！？]{2,}.*"), fields[0]);
                    if (!facts.isEmpty()) assertTrue(result.contains("K2196+226") && result.contains("1.5公里。"));
                    else assertTrue(result.contains("现场影响范围及人员情况尚待核实。"));
                }
                assertThrows(IllegalArgumentException.class, () -> renderer.render(compact, event(),
                        Map.of("现场事实简述", "长".repeat(51)), ""));
                assertThrows(IllegalArgumentException.class, () -> renderer.render(compact, event(),
                        Map.of(), "长".repeat(51)));
            }
        }
    }

    @Test
    void shouldKeepTemplateAndUseSystemFactsBeforeModelValues() {
        String rendered = renderer.render(plan(), event(), Map.of(
                "事件编号", "伪造编号",
                "封控范围", "事发点前后各500米"
        ), "持续关注降雨变化。");

        assertTrue(rendered.contains("INC-001"));
        assertTrue(rendered.contains("福州、G3京台高速"));
        assertTrue(rendered.contains("事发点前后各500米"));
        assertTrue(rendered.contains("已匹配资源清单所列力量"));
        assertTrue(rendered.endsWith("补充建议：持续关注降雨变化。"));
    }

    @Test
    void shouldRejectVariablesOutsidePublishedTemplate() {
        var error = assertThrows(IllegalArgumentException.class,
                () -> renderer.render(plan(), event(), Map.of("到达时间", "5分钟"), ""));
        assertEquals("模型返回了预案不存在的填充项：到达时间", error.getMessage());
    }

    private EmergencyResponsePlanSnapshot plan() {
        return new EmergencyResponsePlanSnapshot(
                "ERP-DT01", "DT01", "崩塌（落石）", 1,
                List.of("覆盖车道"),
                "接报【事件编号】，赶赴【事件地点、路线、方向和桩号】，设置【封控范围】，投入【数据库分配资源】。",
                List.of(new ResponsePlanResourceBaseline(
                        "ROAD_RESCUE_TEAM", 2, "抢通", ResponsePlanResourceMode.BASE)),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        );
    }

    @Test
    void shortModelKeysMapBackToPublishedFieldsAndExcludeSystemFacts() {
        assertEquals(Map.of("F1", "封控范围"), renderer.modelFields(plan(), event()));
        var values = renderer.expandModelFields(plan(), event(), Map.of("F1", "现场核实后设定"));
        assertTrue(renderer.render(plan(), event(), values, "").contains("现场核实后设定"));
        assertThrows(IllegalArgumentException.class, () -> renderer.render(plan(), event(),
                renderer.expandModelFields(plan(), event(), Map.of("F99", "不可信填充")), ""));
        assertThrows(IllegalArgumentException.class, () -> renderer.expandModelFields(plan(), event(),
                Map.of("F1", "值1", "封控范围", "值2")));
    }

    private EmergencyEvent event() {
        return new EmergencyEvent(
                "INC-001", "INC-001", Instant.parse("2026-09-04T00:00:00Z"),
                "DT01", "边坡落石", "350100", "福州", null, null,
                null, "G3", "G3京台高速", 119.2965, 26.0745
        );
    }
}
