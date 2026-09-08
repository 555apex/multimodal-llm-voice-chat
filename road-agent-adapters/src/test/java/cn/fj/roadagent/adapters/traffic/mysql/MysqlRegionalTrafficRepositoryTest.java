package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlRegionalTrafficRepositoryTest {
    private JdbcTemplate jdbc;
    private MysqlRegionalTrafficRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:regional;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_highway_network (
                  id BIGINT PRIMARY KEY, route_code VARCHAR(20), route_name VARCHAR(100),
                  start_place VARCHAR(50), end_place VARCHAR(50), del_flag VARCHAR(2)
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_transport_hubs (
                  id BIGINT PRIMARY KEY, checkpoint_no VARCHAR(100), checkpoint_name VARCHAR(100),
                  route_code VARCHAR(20), route_name VARCHAR(100), stake DECIMAL(10,2),
                  average_speed DECIMAL(10,2), region_code VARCHAR(6), daily_avg_flow BIGINT,
                  temp_2 VARCHAR(30), del_flag VARCHAR(2), create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        repository = new MysqlRegionalTrafficRepository(jdbc,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)), Clock.systemUTC());
    }

    @Test
    void joinsHubsByRouteEndpointsAndIgnoresIncorrectRegionCode() {
        route(1, "G104", "北京-平潭", "宁德市", "福州市", "N");
        hub(1, "N001", "G104", "错误卡口名称", "350200", 100, "700", "N");
        var snapshot = repository.load();
        assertEquals(1, snapshot.hubs().size());
        assertEquals("福州市", snapshot.hubs().get(0).cityAName());
        assertEquals("宁德市", snapshot.hubs().get(0).cityBName());
        assertEquals("北京-平潭", snapshot.hubs().get(0).routeName());
        assertEquals(700, snapshot.hubs().get(0).weeklyTotalFlow());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.contains("权威名称")));
    }

    @Test
    void skipsSameCityAndInvalidRowsButKeepsValidCrossCityData() {
        route(1, "G104", "北京-平潭", "宁德", "福州", "N");
        route(2, "S524", "泉州路线", "泉州", "泉州", "N");
        hub(1, "OK", "G104", "北京-平潭", "350000", 100, "700", "N");
        hub(2, "BAD", "G104", "北京-平潭", "350000", -1, "20", "N");
        hub(3, "SAME", "S524", "泉州路线", "350000", 50, "350", "N");
        var snapshot = repository.load();
        assertEquals(1, snapshot.hubs().size());
        assertTrue(snapshot.warnings().stream().anyMatch(w -> w.contains("已跳过2条")));
    }

    @Test
    void duplicateActiveRouteOrCheckpointIsFatal() {
        route(1, "G104", "北京-平潭", "宁德", "福州", "N");
        route(2, "G104", "重复", "宁德", "福州", "N");
        hub(1, "CP1", "G104", "北京-平潭", "350000", 100, "700", "N");
        assertInvalid();
        jdbc.update("DELETE FROM w_highway_network WHERE id=2");
        hub(2, "CP1", "G104", "北京-平潭", "350000", 100, "700", "N");
        assertInvalid();
    }

    private void assertInvalid() {
        assertEquals("REGIONAL_TRAFFIC_DATA_INVALID",
                assertThrows(ExternalServiceException.class, repository::load).errorCode());
    }
    private void route(long id, String code, String name, String start, String end, String flag) {
        jdbc.update("INSERT INTO w_highway_network VALUES (?,?,?,?,?,?)", id, code, name, start, end, flag);
    }
    private void hub(long id, String checkpoint, String route, String name, String region,
            long daily, String weekly, String flag) {
        jdbc.update("""
                INSERT INTO w_transport_hubs VALUES (?,?,?,?,?,10,40,?,?,?, ?,CURRENT_TIMESTAMP,NULL)
                """, id, checkpoint, checkpoint + "卡口", route, name, region, daily, weekly, flag);
    }
}
