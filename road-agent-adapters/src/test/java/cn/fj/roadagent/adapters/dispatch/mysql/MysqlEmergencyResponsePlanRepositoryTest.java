package cn.fj.roadagent.adapters.dispatch.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlEmergencyResponsePlanRepositoryTest {
    @Test
    void shouldReadOnlyThePublishedVersionForEventType() {
        JdbcTemplate jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:response-plan;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_emergency_response_plan(
                    plan_id VARCHAR(40), event_type VARCHAR(10), event_type_name VARCHAR(50),
                    plan_version INT, plan_status TINYINT, required_facts CLOB,
                    rescue_plan_template CLOB, resource_baseline CLOB, content_hash VARCHAR(64)
                )
                """);
        jdbc.update("""
                INSERT INTO w_emergency_response_plan VALUES
                ('ERP-DT01','DT01','崩塌（落石）',1,1,'[\"覆盖车道\"]',
                 '处置【现场情况】',
                 '[{\"resourceTypeCode\":\"ROAD_RESCUE_TEAM\",\"quantity\":2,\"purpose\":\"抢通\",\"mode\":\"BASE\"}]',
                 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa')
                """);
        var repository = new MysqlEmergencyResponsePlanRepository(
                jdbc, new ObjectMapper().findAndRegisterModules());

        var plan = repository.findActiveByEventType("DT01").orElseThrow();
        assertEquals("ERP-DT01", plan.planId());
        assertEquals(2, plan.resourceBaseline().get(0).quantity());
        assertTrue(repository.findActiveByEventType("ET101").isEmpty());
    }
}
