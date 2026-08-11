package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.event.mysql.AbnormalEventRepository;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.TrafficDataPort;
import cn.fj.roadagent.application.traffic.QueryRealtimeTrafficUseCase;
import cn.fj.roadagent.application.agent.ConverseWithAgentUseCase;
import cn.fj.roadagent.application.dispatch.DispatchApprovalUseCase;
import cn.fj.roadagent.application.speech.QuerySpeechCapabilitiesUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "roadagent.traffic.provider=amap",
        "roadagent.traffic.amap.api-key=test-amap-key",
        "roadagent.model.provider=openai-compatible",
        "roadagent.model.api-key=test-model-key",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
class RoadAgentApplicationTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldAssembleAgentWithoutCallingExternalServices() {
        when(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM w_abnormal_event",
                Integer.class
        )).thenReturn(0);

        assertNotNull(context.getBean(TrafficDataPort.class));
        assertNotNull(context.getBean(QueryRealtimeTrafficUseCase.class));
        assertNotNull(context.getBean(ConverseWithAgentUseCase.class));
        assertNotNull(context.getBean(DispatchApprovalUseCase.class));
        assertNotNull(context.getBean(QuerySpeechCapabilitiesUseCase.class));
        assertEquals(1, context.getBeansOfType(ChatModelPort.class).size());
        assertEquals(0, context.getBean(AbnormalEventRepository.class).countEvents());
    }
}
