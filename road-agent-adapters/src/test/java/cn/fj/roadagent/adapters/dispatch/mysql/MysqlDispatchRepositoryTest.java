package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
import cn.fj.roadagent.domain.dispatch.EmergencyResponsePlanSnapshot;
import cn.fj.roadagent.domain.dispatch.ResponsePlanResourceBaseline;
import cn.fj.roadagent.domain.dispatch.ResponsePlanResourceMode;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlDispatchRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-19T03:00:00Z");

    private JdbcTemplate jdbc;
    private MysqlDispatchRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dispatch-repository;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_emergency_dispatch_order (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    plan_id VARCHAR(40) NOT NULL,
                    event_id VARCHAR(64) NOT NULL,
                    version INT NOT NULL,
                    event_snapshot CLOB,
                    resource_requirements CLOB,
                    resource_list CLOB,
                    resource_shortages CLOB,
                    rescue_plan CLOB,
                    response_plan_id VARCHAR(40),
                    response_plan_version INT,
                    response_plan_snapshot CLOB,
                    order_status TINYINT NOT NULL,
                    rejection_reason VARCHAR(500),
                    error_message VARCHAR(500),
                    approved_time TIMESTAMP,
                    create_time TIMESTAMP NOT NULL,
                    update_time TIMESTAMP NOT NULL,
                    UNIQUE (plan_id, version)
                )
                """);
        repository = new MysqlDispatchRepository(
                jdbc, new ObjectMapper().findAndRegisterModules()
        );
    }

    @Test
    void shouldReadFrozenEventSnapshotAfterSourceEventChanges() {
        EmergencyEvent event = new EmergencyEvent(
                "9007199254740993", "EVT-ORIGINAL", NOW.minusSeconds(60),
                "DT01", "原始现场描述"
        );
        EmergencyResponsePlanSnapshot responsePlan = new EmergencyResponsePlanSnapshot(
                "ERP-DT01", "DT01", "崩塌（落石）", 1, List.of("覆盖车道"),
                "处置【现场情况】", List.of(new ResponsePlanResourceBaseline(
                        "ROAD_RESCUE_TEAM", 1, "抢通", ResponsePlanResourceMode.BASE)),
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        DispatchPlan generating = DispatchPlan.generating("DP-1", event, 1L, NOW, responsePlan);
        assertTrue(repository.insert(generating));

        DispatchPlan restored = repository.findLatestByPlanId("DP-1").orElseThrow();
        assertEquals("EVT-ORIGINAL", restored.event().customId());
        assertEquals("原始现场描述", restored.event().description());
        assertEquals("9007199254740993", restored.event().eventId());
        assertEquals("ERP-DT01", restored.responsePlan().planId());

        DispatchPlan generated = generating.generated(
                List.of(new SuggestedResource(
                        "抢险队伍", "道路抢险人员", 1, "组", "现场抢通"
                )),
                "设置警戒并组织抢通。", NOW.plusSeconds(1)
        );
        assertTrue(repository.updateGenerated(generated));
        assertEquals(DispatchStatus.WAITING_APPROVAL,
                repository.findVersion("DP-1", 1L).orElseThrow().status());
    }
}
