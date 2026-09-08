package cn.fj.roadagent.adapters.event.mysql;

import cn.fj.roadagent.domain.dispatch.WorkflowStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbnormalEventRepositoryTest {
    private JdbcTemplate jdbc;
    private AbnormalEventRepository repository;

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:lw-events;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_lw_incident (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, c_no VARCHAR(64) NOT NULL UNIQUE,
                    c_type VARCHAR(16), event_type VARCHAR(10), content CLOB, guard_time TIMESTAMP,
                    source_name VARCHAR(64), source_org_name VARCHAR(64), place VARCHAR(128),
                    route_no VARCHAR(32), route_name VARCHAR(64), lon DECIMAL(10,6), lat DECIMAL(9,6),
                    status VARCHAR(16), completed TINYINT, deleted TINYINT
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_dispatch_workflow (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, workflow_id VARCHAR(40) NOT NULL,
                    event_id VARCHAR(64) NOT NULL, current_stage TINYINT,
                    workflow_status TINYINT NOT NULL, stage_entered_at TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_event_classification (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(64), attempt_no INT,
                    classification_status TINYINT, create_time TIMESTAMP
                )
                """);
        repository = new AbnormalEventRepository(jdbc);
    }

    @Test
    void shouldOnlyReadEligibleEmergencyIncidentsAndUseCno() {
        insert("EM-1", "4", "1", 0, 0, "DT01", "福州市闽侯县路段落石", "2026-08-01T01:00:00Z");
        insert("NON-EM", "1", "1", 0, 0, "ET110", "计划施工", "2026-08-01T00:00:00Z");
        insert("DONE", "4", "2", 1, 0, "ET106", "事故已完成", "2026-08-01T00:00:00Z");
        insert("DELETED", "4", "1", 0, 1, "ET106", "已删除", "2026-08-01T00:00:00Z");
        insert("UNCLASSIFIED", "4", "1", 0, 0, null, "史无已知类型", "2026-08-01T00:00:00Z");

        var event = repository.findNextPending().orElseThrow();
        assertEquals("EM-1", event.eventId());
        assertEquals(1, repository.countPending());
        assertEquals("福州", event.cityName());
        assertEquals(1, repository.countUnclassified());
        assertTrue(repository.findUnclassifiedById("UNCLASSIFIED").isPresent());
        assertFalse(repository.findPendingById("NON-EM").isPresent());
    }

    @Test
    void shouldAssignClassifyCorrectAndCompleteWithConditionalUpdates() {
        insert("EM-A", "4", "1", 0, 0, null, "两车追尾", "2026-08-01T01:00:00Z");
        assertTrue(repository.assignEventTypeIfAbsent("EM-A", "ET106"));
        assertFalse(repository.assignEventTypeIfAbsent("EM-A", "DT01"));
        assertTrue(repository.correctEventType("EM-A", "ET106", "ET107"));
        assertFalse(repository.correctEventType("EM-A", "ET106", "DT01"));
        assertTrue(repository.lockPendingById("EM-A").isPresent());
        assertTrue(repository.markDispatchApproved("EM-A", Instant.EPOCH));
        assertEquals("2", jdbc.queryForObject(
                "SELECT status FROM w_lw_incident WHERE c_no='EM-A'", String.class));
        assertEquals(1, jdbc.queryForObject(
                "SELECT completed FROM w_lw_incident WHERE c_no='EM-A'", Integer.class));
    }

    @Test
    void shouldPutEachEventInExactlyOneWorkflowInbox() {
        insert("E31", "4", "1", 0, 0, "DT01", "落石", "2026-08-01T01:00:00Z");
        insert("E32", "4", "1", 0, 0, "DT02", "滑坡", "2026-08-01T02:00:00Z");
        insert("E33", "4", "1", 0, 0, "DT03", "泥石流", "2026-08-01T03:00:00Z");
        jdbc.update("""
                INSERT INTO w_emergency_dispatch_workflow
                (workflow_id,event_id,current_stage,workflow_status,stage_entered_at)
                VALUES ('WF-32','E32',2,3,CURRENT_TIMESTAMP),('WF-33','E33',3,4,CURRENT_TIMESTAMP)
                """);
        assertEquals(1, repository.countPendingForStage(WorkflowStage.LEVEL_1));
        assertEquals(1, repository.countPendingForStage(WorkflowStage.LEVEL_2));
        assertEquals(1, repository.countPendingForStage(WorkflowStage.LEVEL_3));
    }

    @Test
    void shouldExcludeEmbeddedContactDetailsFromEventDescription() {
        insert("CONTACT", "4", "1", 0, 0, "ET106",
                "G205发生交通事故，现场联系人：林某00000000000。",
                "2026-08-01T01:00:00Z");

        var event = repository.findPendingById("CONTACT").orElseThrow();

        assertEquals("G205发生交通事故", event.description());
        assertFalse(event.description().contains("联系人"));
        assertFalse(event.description().contains("00000000000"));
    }

    @Test
    void shouldUseSafePlaceholderWhenContentOnlyContainsContactDetails() {
        assertEquals("事件详情待核实",
                AbnormalEventRepository.sanitizeDescription("联系人：张某00000000001。"));
    }

    private void insert(String cNo, String cType, String status, int completed, int deleted,
                        String eventType, String content, String time) {
        jdbc.update("""
                INSERT INTO w_lw_incident
                (c_no,c_type,event_type,content,guard_time,source_name,place,route_no,route_name,
                 lon,lat,status,completed,deleted)
                VALUES (?,?,?,?,?,'福州高速路管理中心','闽侯县','G70','福银高速',119.2,26.1,?,?,?)
                """, cNo, cType, eventType, content, java.sql.Timestamp.from(Instant.parse(time)),
                status, completed, deleted);
    }
}
