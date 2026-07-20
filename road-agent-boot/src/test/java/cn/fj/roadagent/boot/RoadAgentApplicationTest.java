package cn.fj.roadagent.boot;

import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "roadagent.traffic.provider=mock",
        "roadagent.model.provider=rule"
})
class RoadAgentApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void shouldStartWithoutExternalApiKeys() {
        assertNotNull(context.getBean(TrafficDataPort.class));
        assertNotNull(context.getBean(QueryRealtimeTrafficUseCase.class));
        assertEquals(0, context.getBeansOfType(ChatModelPort.class).size());
    }
}
