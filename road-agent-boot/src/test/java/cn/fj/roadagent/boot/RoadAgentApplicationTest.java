package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.event.mysql.AbnormalEventRepository;
import cn.fj.roadagent.adapters.dispatch.mysql.MysqlDispatchRepository;
import cn.fj.roadagent.adapters.dispatch.mysql.MysqlEmergencyResourceRepository;
import cn.fj.roadagent.adapters.dispatch.mysql.MysqlResourceAllocationRepository;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotPort;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotPort;
import cn.fj.roadagent.application.port.ResourceAllocationPort;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.application.traffic.QueryHighwayTrafficUseCase;
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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "roadagent.traffic.snapshot-poll-seconds=60",
        "roadagent.traffic.snapshot-stable-seconds=30",
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
                "SELECT COUNT(*) FROM w_lw_incident",
                Integer.class
        )).thenReturn(0);

        assertNotNull(context.getBean(HighwayTrafficSnapshotPort.class));
        assertNotNull(context.getBean(RoadCapacitySnapshotPort.class));
        assertNotNull(context.getBean(QueryHighwayTrafficUseCase.class));
        assertNotNull(context.getBean(ConverseWithAgentUseCase.class));
        assertNotNull(context.getBean(DispatchApprovalUseCase.class));
        assertNotNull(context.getBean(QuerySpeechCapabilitiesUseCase.class));
        assertEquals(1, context.getBeansOfType(ChatModelPort.class).size());
        assertInstanceOf(MysqlDispatchRepository.class, context.getBean(DispatchRepository.class));
        assertInstanceOf(MysqlEmergencyResourceRepository.class,
                context.getBean(ResourceDataPort.class));
        assertInstanceOf(MysqlResourceAllocationRepository.class,
                context.getBean(ResourceAllocationPort.class));
        assertEquals(0, context.getBean(AbnormalEventRepository.class).countEvents());
    }
}
