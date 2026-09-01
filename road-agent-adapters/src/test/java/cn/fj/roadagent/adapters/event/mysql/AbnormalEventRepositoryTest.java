package cn.fj.roadagent.adapters.event.mysql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;

import cn.fj.roadagent.domain.dispatch.WorkflowStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbnormalEventRepositoryTest {
    private JdbcTemplate jdbcTemplate;
    private AbnormalEventRepository repository;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:abnormal-events;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "sa",
                ""
        );
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS w_emergency_dispatch_workflow");
        jdbcTemplate.execute("DROP TABLE IF EXISTS w_abnormal_event");
        jdbcTemplate.execute("""
                CREATE TABLE w_abnormal_event (
                    id BIGINT PRIMARY KEY,
                    custom_id VARCHAR(64),
                    occurrence_time TIMESTAMP,
                    event_type VARCHAR(16),
                    description VARCHAR(255),
                    event_city_code VARCHAR(12),
                    event_city_name VARCHAR(32),
                    create_time TIMESTAMP,
                    event_status INT,
                    del_flag VARCHAR(1),
                    no_dispatch_reason VARCHAR(255),
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE w_emergency_dispatch_workflow (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    workflow_id VARCHAR(40) NOT NULL,
                    event_id BIGINT NOT NULL,
                    current_stage TINYINT,
                    workflow_status TINYINT NOT NULL,
                    stage_entered_at TIMESTAMP
                )
                """);
        repository = new AbnormalEventRepository(jdbcTemplate);
    }

    @Test
    void shouldOnlyReturnPendingEventsWithActiveDeletionFlags() {
        insert(1, 0, "N", "2026-08-01T01:00:00Z");
        insert(2, 0, "0", "2026-08-01T02:00:00Z");
        insert(3, 0, null, "2026-08-01T03:00:00Z");
        insert(4, 0, "Y", "2026-07-01T01:00:00Z");
        insert(5, 0, "1", "2026-07-01T02:00:00Z");
        insert(6, 1, "N", "2026-07-01T03:00:00Z");
        insert(7, 2, "N", "2026-07-01T04:00:00Z");

        assertEquals("1", repository.findNextPending().orElseThrow().eventId());
        assertEquals(3, repository.countPending());
        assertTrue(repository.findPendingById("1").isPresent());
        assertTrue(repository.findPendingById("2").isPresent());
        assertTrue(repository.findPendingById("3").isPresent());
        assertFalse(repository.findPendingById("4").isPresent());
        assertFalse(repository.findPendingById("5").isPresent());
        assertFalse(repository.findPendingById("6").isPresent());
        assertFalse(repository.findPendingById("7").isPresent());
    }

    @Test
    void shouldLockAndUpdateCurrentNFlagEvents() {
        insert(11, 0, "N", "2026-08-01T01:00:00Z");
        insert(12, 0, "N", "2026-08-01T02:00:00Z");

        assertTrue(repository.lockPendingById("11").isPresent());
        assertTrue(repository.markDispatchApproved("11", Instant.parse("2026-08-05T10:00:00Z")));
        assertTrue(repository.markNoDispatch(
                "12",
                "现场已恢复",
                Instant.parse("2026-08-05T10:01:00Z")
        ));

        assertEquals(1, statusOf(11));
        assertEquals(2, statusOf(12));
        assertEquals(
                "现场已恢复",
                jdbcTemplate.queryForObject(
                        "SELECT no_dispatch_reason FROM w_abnormal_event WHERE id = 12",
                        String.class
                )
        );
        assertEquals(0, repository.countPending());
    }

    @Test
    void shouldNotUpdateLogicallyDeletedEvents() {
        insert(21, 0, "Y", "2026-08-01T01:00:00Z");
        insert(22, 0, "1", "2026-08-01T02:00:00Z");

        assertFalse(repository.markDispatchApproved("21", Instant.EPOCH));
        assertFalse(repository.markNoDispatch("22", "ignore", Instant.EPOCH));
        assertEquals(0, statusOf(21));
        assertEquals(0, statusOf(22));
    }

    @Test
    void shouldPutEachEventInExactlyOneWorkflowInbox() {
        insert(31, 0, "N", "2026-08-01T01:00:00Z");
        insert(32, 0, "N", "2026-08-01T02:00:00Z");
        insert(33, 0, "N", "2026-08-01T03:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO w_emergency_dispatch_workflow (
                    workflow_id, event_id, current_stage, workflow_status, stage_entered_at
                ) VALUES (?, ?, ?, ?, ?), (?, ?, ?, ?, ?)
                """,
                "WF-32", 32L, 2, 3, java.sql.Timestamp.from(Instant.parse("2026-08-02T00:00:00Z")),
                "WF-33", 33L, 3, 4, java.sql.Timestamp.from(Instant.parse("2026-08-03T00:00:00Z"))
        );

        assertEquals(1, repository.countPendingForStage(WorkflowStage.LEVEL_1));
        assertEquals(1, repository.countPendingForStage(WorkflowStage.LEVEL_2));
        assertEquals(1, repository.countPendingForStage(WorkflowStage.LEVEL_3));
        assertEquals("31", repository.findNextPendingForStage(WorkflowStage.LEVEL_1).orElseThrow().eventId());
        assertEquals("32", repository.findNextPendingForStage(WorkflowStage.LEVEL_2).orElseThrow().eventId());
        assertEquals("33", repository.findNextPendingForStage(WorkflowStage.LEVEL_3).orElseThrow().eventId());
    }

    private void insert(long id, int status, String delFlag, String occurrenceTime) {
        jdbcTemplate.update(
                """
                INSERT INTO w_abnormal_event (
                    id, custom_id, occurrence_time, event_type, description,
                    create_time, event_status, del_flag
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "EVT-" + id,
                java.sql.Timestamp.from(Instant.parse(occurrenceTime)),
                "DT01",
                "测试事件" + id,
                java.sql.Timestamp.from(Instant.parse(occurrenceTime)),
                status,
                delFlag
        );
    }

    private int statusOf(long id) {
        return jdbcTemplate.queryForObject(
                "SELECT event_status FROM w_abnormal_event WHERE id = ?",
                Integer.class,
                id
        );
    }
}
