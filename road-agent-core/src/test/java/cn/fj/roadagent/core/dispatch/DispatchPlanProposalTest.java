package cn.fj.roadagent.core.dispatch;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DispatchPlanProposalTest {

    @Test
    void shouldKeepStringRescuePlan() {
        DispatchPlanProposal proposal = new DispatchPlanProposal(List.of(), " 先警戒，再抢通。 ");

        assertEquals("先警戒，再抢通。", proposal.rescuePlanText());
    }

    @Test
    void shouldNormalizeTextSectionsReturnedAsJsonObject() {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("现场安全", "划定危险区域并持续监测边坡。");
        sections.put("交通组织", "实施临时管制并引导车辆绕行。");
        sections.put("救援处置", "确认安全后组织机械清障。");
        sections.put("信息报送", "持续报送现场进展。");

        DispatchPlanProposal proposal = new DispatchPlanProposal(List.of(), sections);

        assertEquals("""
                现场安全：划定危险区域并持续监测边坡。
                交通组织：实施临时管制并引导车辆绕行。
                救援处置：确认安全后组织机械清障。
                信息报送：持续报送现场进展。""", proposal.rescuePlanText());
    }

    @Test
    void shouldRejectNestedOrArrayRescuePlan() {
        DispatchPlanProposal nested = new DispatchPlanProposal(
                List.of(), Map.of("现场安全", Map.of("措施", "警戒"))
        );
        DispatchPlanProposal array = new DispatchPlanProposal(
                List.of(), List.of("警戒", "抢通")
        );

        assertThrows(IllegalArgumentException.class, nested::rescuePlanText);
        assertThrows(IllegalArgumentException.class, array::rescuePlanText);
    }
}
