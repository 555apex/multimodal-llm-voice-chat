package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class MysqlOdTrafficRepositoryTest {
    JdbcTemplate jdbc;
    MysqlOdTrafficRepository repository;

    @BeforeEach void setup() {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:od;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("CREATE TABLE w_region_code (code VARCHAR(6), name VARCHAR(30), del_flag VARCHAR(2))");
        jdbc.execute("""
                CREATE TABLE w_transport_hubs (
                  id BIGINT PRIMARY KEY, checkpoint_no VARCHAR(30), region_code VARCHAR(6),
                  route_code VARCHAR(20), route_name VARCHAR(100), average_speed DECIMAL(10,2),
                  daily_avg_flow BIGINT, temp_2 VARCHAR(255), temp_3 VARCHAR(255),
                  del_flag VARCHAR(2), create_time TIMESTAMP, update_time TIMESTAMP)
                """);
        jdbc.update("INSERT INTO w_region_code VALUES ('350100','福州市','N'),('350200','厦门市','0')");
        repository = new MysqlOdTrafficRepository(jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)),
                new ObjectMapper(), Clock.systemUTC());
        insert(1, "F1", "350100", "N", "151", "{\"car\":75,\"bus\":40,\"truck\":36}");
        insert(2, "X1", "350200", "N", "0", "{\"car\":0,\"bus\":0,\"truck\":0}");
    }

    @Test void loadsSeparateWeeklyAndDailyNumbersWithZeroAndPartialCities() {
        var s = repository.load(List.of("350100", "350200", "350800"), true);
        assertEquals(2, s.hubs().size());
        assertEquals(151, s.hubs().get(0).weeklyTotalFlow());
        assertEquals(10, s.hubs().get(0).dailyAverageFlow());
        assertEquals(75L, s.hubs().get(0).carWeeklyFlow());
        assertEquals(0, s.hubs().get(1).weeklyTotalFlow());
        assertTrue(s.warnings().isEmpty());
    }

    @Test void filtersBeforeParsingAndOnlyLoadsVehiclesWhenNeeded() {
        jdbc.update("UPDATE w_transport_hubs SET temp_3='broken' WHERE id=2");
        assertEquals(1, repository.load(List.of("350100"), true).hubs().size());
        assertNull(repository.load(List.of("350200"), false).hubs().get(0).carWeeklyFlow());
        assertInvalid(() -> repository.load(List.of("350200"), true));
    }

    @Test void toleratesSumMismatchWithoutRepairAndSupportsDelFlagVariants() {
        jdbc.update("UPDATE w_transport_hubs SET temp_2='999',del_flag=NULL WHERE id=1");
        var snapshot = repository.load(List.of("350100"), true);
        assertEquals(999, snapshot.hubs().get(0).weeklyTotalFlow());
        assertEquals(1, snapshot.warnings().size());
        jdbc.update("UPDATE w_transport_hubs SET del_flag='Y' WHERE id=1");
        assertTrue(repository.load(List.of("350100"), true).hubs().isEmpty());
        jdbc.update("UPDATE w_transport_hubs SET del_flag='0' WHERE id=1");
        assertEquals(1, repository.load(List.of("350100"), true).hubs().size());
    }

    @Test void rejectsDuplicateCheckpointsUnknownCitiesAndNames() {
        insert(3, "F1", "350100", "N", "1", "{\"car\":1,\"bus\":0,\"truck\":0}");
        assertInvalid(() -> repository.load(List.of("350100"), true));
        jdbc.update("DELETE FROM w_transport_hubs WHERE id=3");
        jdbc.update("UPDATE w_region_code SET name='厦门市' WHERE code='350100'");
        assertInvalid(() -> repository.load(List.of("350100"), true));
        assertInvalid(() -> repository.load(List.of("999999"), true));
    }

    @Test void rejectsBadIntegerJsonTypesMissingKeysAndOverflow() {
        for (String json : List.of("{}", "null", "[]", "{\"car\":1.5,\"bus\":0,\"truck\":0}",
                "{\"car\":-1,\"bus\":0,\"truck\":0}", "{\"car\":\"1\",\"bus\":0,\"truck\":0}",
                "{\"car\":1,\"car\":2,\"bus\":0,\"truck\":0}",
                "{\"car\":9223372036854775808,\"bus\":0,\"truck\":0}")) {
            jdbc.update("UPDATE w_transport_hubs SET temp_3=? WHERE id=1", json);
            assertInvalid(() -> repository.load(List.of("350100"), true));
        }
        for (String weekly : List.of("-1", "1.5", "abc", "", "9223372036854775808")) {
            jdbc.update("UPDATE w_transport_hubs SET temp_2=? WHERE id=1", weekly);
            assertInvalid(() -> repository.load(List.of("350100"), false));
        }
        jdbc.update("UPDATE w_transport_hubs SET temp_2=NULL WHERE id=1");
        assertInvalid(() -> repository.load(List.of("350100"), false));
    }

    @Test void cityOnlyQueryDoesNotRequireRouteNamesOrVehicleColumns() {
        jdbc.update("UPDATE w_transport_hubs SET route_name='other' WHERE id=2");
        assertEquals(2, repository.load(List.of("350100", "350200"), false).hubs().size());
        assertInvalid(() -> repository.load(List.of("350100", "350200"), true));
        jdbc.execute("ALTER TABLE w_transport_hubs DROP COLUMN temp_3");
        assertEquals(2, repository.load(List.of("350100", "350200"), false).hubs().size());
    }

    private void insert(long id, String checkpoint, String code, String flag, String weekly, String json) {
        jdbc.update("""
                INSERT INTO w_transport_hubs VALUES (?, ?, ?, 'G324', '福州—昆明', 0, 10, ?, ?, ?,
                    TIMESTAMP '2026-09-03 12:00:00', NULL)
                """, id, checkpoint, code, weekly, json, flag);
    }

    private void assertInvalid(org.junit.jupiter.api.function.Executable query) {
        assertEquals("OD_ANALYSIS_DATA_INVALID", assertThrows(ExternalServiceException.class, query).errorCode());
    }
}
