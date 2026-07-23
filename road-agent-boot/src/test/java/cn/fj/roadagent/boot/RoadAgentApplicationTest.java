package cn.fj.roadagent.boot;

import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "roadagent.traffic.provider=amap",
        "roadagent.traffic.amap.api-key=test-amap-key",
        "roadagent.model.provider=openai-compatible",
        "roadagent.model.api-key=test-model-key"
})
class RoadAgentApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldAssembleAgentWithoutCallingExternalServices() {
        assertNotNull(context.getBean(TrafficDataPort.class));
        assertNotNull(context.getBean(QueryRealtimeTrafficUseCase.class));
        assertNotNull(context.getBean(ConverseWithAgentUseCase.class));
        assertNotNull(context.getBean(DispatchApprovalUseCase.class));
        assertEquals(1, context.getBeansOfType(ChatModelPort.class).size());
    }
}
