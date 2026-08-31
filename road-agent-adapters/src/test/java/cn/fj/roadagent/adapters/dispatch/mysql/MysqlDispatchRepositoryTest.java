package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.EmergencyEvent;
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
                CREATE TABLE w_abnormal_event (
                    id BIGINT PRIMARY KEY,
                    custom_id VARCHAR(64),
                    occurrence_time TIMESTAMP,
                    event_type VARCHAR(16),
                    description VARCHAR(255),
                    event_city_code VARCHAR(12),
                    event_city_name VARCHAR(32)
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_dispatch_order (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    plan_id VARCHAR(40) NOT NULL,
                    event_id BIGINT NOT NULL,
                    version INT NOT NULL,
                    event_snapshot CLOB,
                    resource_requirements CLOB,
                    resource_list CLOB,
                    resource_shortages CLOB,
                    rescue_plan CLOB,
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
        jdbc.update(
                "INSERT INTO w_abnormal_event (id, custom_id, occurrence_time, event_type, description) VALUES (?, ?, ?, ?, ?)",
                Long.parseLong(event.eventId()), event.customId(),
                Timestamp.from(event.occurrenceTime()), event.eventType(), event.description()
        );
        DispatchPlan generating = DispatchPlan.generating("DP-1", event, 1L, NOW);
        assertTrue(repository.insert(generating));

        jdbc.update(
                "UPDATE w_abnormal_event SET custom_id = ?, description = ? WHERE id = ?",
                "EVT-CHANGED", "事件源后来被修改", Long.parseLong(event.eventId())
        );

        DispatchPlan restored = repository.findLatestByPlanId("DP-1").orElseThrow();
        assertEquals("EVT-ORIGINAL", restored.event().customId());
        assertEquals("原始现场描述", restored.event().description());
        assertEquals("9007199254740993", restored.event().eventId());

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
