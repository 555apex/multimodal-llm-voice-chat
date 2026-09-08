package cn.fj.roadagent.adapters.traffic.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlTrafficContextEventRepositoryTest {
    private JdbcTemplate jdbc;
    private MysqlTrafficContextEventRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:festival;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_festival_data (
                  id BIGINT PRIMARY KEY, event_code VARCHAR(64), event_name VARCHAR(100),
                  event_type VARCHAR(24), event_start_time TIMESTAMP, event_end_time TIMESTAMP,
                  impact_start_time TIMESTAMP, impact_end_time TIMESTAMP, scope_type VARCHAR(16),
                  region_code VARCHAR(6), region_name VARCHAR(20), affected_route_codes VARCHAR(200),
                  venue VARCHAR(200), data_status VARCHAR(16), enabled INT, del_flag VARCHAR(2)
                )
                """);
        repository = new MysqlTrafficContextEventRepository(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper()
        );
    }

    @Test
    void readsOnlyActiveRowsAtShanghaiTrafficTimeAndParsesRoutes() {
        insert(1, "DEMO_ACTIVE", "福州市大型体育赛事（模拟）", "SPORTS_EVENT",
                "2026-08-13 08:00:00", "2026-08-13 12:00:00",
                "2026-08-13 06:00:00", "2026-08-13 14:00:00",
                "CITY", "350100", "[\"G104\",\"G316\"]", 1, "N");
        insert(2, "OUTSIDE", "时间外活动", "CONCERT",
                "2026-08-14 08:00:00", "2026-08-14 12:00:00",
                "2026-08-14 06:00:00", "2026-08-14 14:00:00",
                "CITY", "350100", "[\"G104\"]", 1, "N");
        insert(3, "DISABLED", "停用活动", "CONCERT",
                "2026-08-13 08:00:00", "2026-08-13 12:00:00",
                "2026-08-13 06:00:00", "2026-08-13 14:00:00",
                "CITY", "350100", "[\"G104\"]", 0, "N");
        insert(4, "INVALID", "非法路线活动", "SPORTS_EVENT",
                "2026-08-13 08:00:00", "2026-08-13 12:00:00",
                "2026-08-13 06:00:00", "2026-08-13 14:00:00",
                "ROUTE", null, "[]", 1, "N");

        var events = repository.findActiveAt(Instant.parse("2026-08-13T01:00:00Z"));

        assertEquals(1, events.size());
        assertEquals("DEMO_ACTIVE", events.get(0).eventCode());
        assertEquals("350100", events.get(0).regionCode());
        assertEquals(java.util.Set.of("G104", "G316"), events.get(0).affectedRouteCodes());
    }

    @Test
    void missingSupplementaryTableDoesNotBreakCoreTraffic() {
        jdbc.execute("DROP TABLE w_festival_data");
        assertTrue(repository.findActiveAt(Instant.parse("2026-08-13T01:00:00Z")).isEmpty());
    }

    private void insert(long id, String code, String name, String type,
            String eventStart, String eventEnd, String impactStart, String impactEnd,
            String scope, String regionCode, String routes, int enabled, String delFlag) {
        jdbc.update("""
                INSERT INTO w_festival_data VALUES (
                  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DEMO', ?, ?
                )
                """, id, code, name, type, eventStart, eventEnd, impactStart, impactEnd,
                scope, regionCode, regionCode == null ? null : "福州市", routes,
                regionCode == null ? null : "测试场馆", enabled, delFlag);
    }
}
