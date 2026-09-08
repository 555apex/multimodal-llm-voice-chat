package cn.fj.roadagent.boot;

import cn.fj.roadagent.core.dispatch.DispatchPlanProposal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchPlanProposalJsonBindingTest {

    @Test
    void shouldBindPublishedPlanVariablesAndSupplementalAdvice() throws Exception {
        DispatchPlanProposal proposal = new ObjectMapper().readValue("""
                {
                  "templateVariables": {"封控范围": "事发点前后各500米"},
                  "resourceRequirements": [{
                    "resourceTypeCode": "ROAD_RESCUE_TEAM",
                    "quantity": 2,
                    "purpose": "负责现场抢通"
                  }],
                  "supplementalAdvice": "持续关注降雨变化。"
                }
                """, DispatchPlanProposal.class);

        assertEquals("事发点前后各500米", proposal.templateVariables().get("封控范围"));
        assertEquals("持续关注降雨变化。", proposal.supplementalAdviceText());
    }

    @Test
    void shouldBindAndNormalizeSectionedRescuePlanReturnedByCompatibleModel() throws Exception {
        DispatchPlanProposal proposal = new ObjectMapper().readValue("""
                {
                  "resourceRequirements": [
                    {
                      "resourceTypeCode": "ROAD_RESCUE_TEAM",
                      "quantity": 2,
                      "purpose": "负责现场抢通"
                    }
                  ],
                  "rescuePlan": {
                    "现场安全": "划定危险区并监测边坡。",
                    "交通组织": "实施临时管制并引导绕行。",
                    "救援处置": "确认安全后组织机械清障。",
                    "信息报送": "持续报送现场进展。"
                  }
                }
                """, DispatchPlanProposal.class);

        assertEquals(1, proposal.resourceRequirements().size());
        assertEquals("""
                现场安全：划定危险区并监测边坡。
                交通组织：实施临时管制并引导绕行。
                救援处置：确认安全后组织机械清障。
                信息报送：持续报送现场进展。""", proposal.rescuePlanText());
    }
}
