package cn.fj.roadagent.core.agent;

import cn.fj.roadagent.application.agent.AgentDecision;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.model.ModelResponse;
import cn.fj.roadagent.application.model.ModelStreamListener;
import cn.fj.roadagent.application.port.ChatModelPort;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntentPlannerTest {

    @Test
    void shouldAllowAreaQueryWithoutRoadName() {
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "AREA_ALL", "厦门", "思明区", null, null,
                null, null, null, null, List.of(), null
        );

        assertTrue(new IntentPlanner(new FixedModel(decision)).missingFields(decision).isEmpty());
    }

    @Test
    void shouldKeepRoadNameMandatoryOnlyForRoadScope() {
        AgentDecision decision = new AgentDecision(
                "TRAFFIC_QUERY", "ROAD", "厦门", null, null, null,
                null, null, null, null, List.of(), null
        );

        assertEquals(List.of("roadName"), new IntentPlanner(new FixedModel(decision)).missingFields(decision));
    }

    @Test
    void promptShouldDefineThreeTrafficScopesAndForbidInventedMajorRoads() {
        FixedModel model = new FixedModel(new AgentDecision(
                "TRAFFIC_QUERY", "AREA_MAJOR", null, "思明区", null, null,
                null, null, null, null, List.of(), null
        ));

        new IntentPlanner(model).plan("思明区交通要道如何", List.of());

        assertTrue(model.lastRequest.systemPrompt().contains("ROAD、AREA_ALL、AREA_MAJOR"));
        assertTrue(model.lastRequest.systemPrompt().contains("不得凭自身知识列举道路"));
    }

    private static final class FixedModel implements ChatModelPort {
        private final AgentDecision decision;
        private ModelRequest lastRequest;

        private FixedModel(AgentDecision decision) {
            this.decision = decision;
        }

        @Override
        public ModelResponse generate(ModelRequest request) {
            return new ModelResponse("", "TEST", "test");
        }

        @Override
        public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            lastRequest = request;
            return resultType.cast(decision);
        }

        @Override
        public void stream(ModelRequest request, ModelStreamListener listener) {
            throw new UnsupportedOperationException();
        }
    }
}
