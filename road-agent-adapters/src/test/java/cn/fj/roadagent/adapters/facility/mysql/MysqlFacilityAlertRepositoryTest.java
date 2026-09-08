package cn.fj.roadagent.adapters.facility.mysql;

import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlFacilityAlertRepositoryTest {
    private JdbcTemplate jdbc;
    private MysqlFacilityAlertRepository repository;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:facility-alert;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_realtime_abnormal (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  facility_name VARCHAR(50), metric_name VARCHAR(50) NOT NULL,
                  actual_value DECIMAL(15,6), actual_str_value VARCHAR(100),
                  threshold_min DECIMAL(15,6), threshold_max DECIMAL(15,6),
                  alarm_level TINYINT NOT NULL, collect_time TIMESTAMP NOT NULL,
                  trigger_time TIMESTAMP NOT NULL, status TINYINT NOT NULL,
                  remark VARCHAR(255)
                )
                """);
        repository = new MysqlFacilityAlertRepository(jdbc);
    }

    @Test
    void shouldFilterOrderCountAndConditionallyUpdate() {
        insert("A桥", "位移", 1, 1, Instant.parse("2026-09-08T01:00:00Z"));
        insert("B隧道", "沉降", 3, 1, Instant.parse("2026-09-08T01:30:00Z"));
        insert("C边坡", "含水率", 2, 2, Instant.parse("2026-09-08T00:30:00Z"));

        var pending = repository.findPage(FacilityAlertStatus.PENDING, null, 0, 20);
        assertEquals(2, pending.size());
        assertEquals("B隧道", pending.get(0).facilityName());
        assertEquals(1, repository.count(FacilityAlertStatus.PENDING, AlarmLevel.WARNING));
        assertEquals(2, repository.counts().pending());
        assertEquals(1, repository.counts().confirmed());
        assertEquals(3, repository.findActive().size());

        long id = pending.get(0).alertId();
        assertTrue(repository.updateStatus(
                id, FacilityAlertStatus.PENDING, FacilityAlertStatus.CONFIRMED, "【已确认】已派员"
        ));
        assertFalse(repository.updateStatus(
                id, FacilityAlertStatus.PENDING, FacilityAlertStatus.CONFIRMED, "重复"
        ));
        assertEquals(FacilityAlertStatus.CONFIRMED, repository.findById(id).orElseThrow().status());
        assertEquals("【已确认】已派员", repository.findById(id).orElseThrow().remark());
    }

    private void insert(String facility, String metric, int level, int status, Instant trigger) {
        jdbc.update("""
                INSERT INTO w_realtime_abnormal (
                  facility_name, metric_name, actual_value, threshold_max,
                  alarm_level, collect_time, trigger_time, status
                ) VALUES (?, ?, 12.5, 10, ?, ?, ?, ?)
                """, facility, metric, level, Timestamp.from(trigger.plusSeconds(30)),
                Timestamp.from(trigger), status);
    }
}
