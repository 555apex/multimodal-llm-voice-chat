package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.domain.traffic.CapacityLevel;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlRoadCapacitySnapshotSourceTest {
    private JdbcTemplate jdbc;
    private MysqlRoadCapacitySnapshotSource source;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:capacity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_highway_network (
                  id BIGINT PRIMARY KEY, route_code VARCHAR(20), route_name VARCHAR(100),
                  road_type VARCHAR(20), start_place VARCHAR(50), end_place VARCHAR(50), del_flag VARCHAR(2)
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_road_capacity (
                  id BIGINT PRIMARY KEY, route_code VARCHAR(20), route_name VARCHAR(100),
                  design_flow DECIMAL(10,2), avg_previous_hour DECIMAL(10,2),
                  utilization_perc DECIMAL(10,4), del_flag VARCHAR(2),
                  create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        source = new MysqlRoadCapacitySnapshotSource(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), Clock.systemUTC()
        );
    }

    @Test
    void loadsDatabaseResultsIncludingValidZeroAndIgnoresLogicalDeletion() {
        insertRoute(1, "G104", "北京-平潭", null);
        insertCapacity(1, "G104", "北京-平潭", 1920, 0, 0, null);
        insertRoute(2, "S201", "柘荣-霞浦", "1");
        insertCapacity(2, "S201", "柘荣-霞浦", 1920, 100, 0.1, "1");

        var snapshot = source.loadCandidate();

        assertEquals(1, snapshot.capacities().size());
        assertEquals(0, snapshot.capacities().get(0).actualCapacityVph());
        assertEquals(CapacityLevel.NORMAL, snapshot.capacities().get(0).level());
    }

    @Test
    void allowsPartialCoverageButRejectsDuplicateAndNameMismatch() {
        insertRoute(1, "G104", "北京-平潭", null);
        insertRoute(2, "S201", "柘荣-霞浦", null);
        insertCapacity(1, "G104", "北京-平潭", 1920, 100, 0.1, null);

        var partial = source.loadCandidate();
        assertEquals(1, partial.capacities().size());
        assertEquals("G104", partial.capacities().get(0).routeCode());

        jdbc.update("UPDATE w_road_capacity SET route_name='错误名称' WHERE id=1");
        assertRefreshing();

        jdbc.update("UPDATE w_road_capacity SET route_name='北京-平潭' WHERE id=1");
        insertCapacity(3, "G104", "北京-平潭", 1920, 200, 0.2, null);
        assertRefreshing();
    }

    @Test
    void rejectsCapacityRouteOutsideActiveNetwork() {
        insertRoute(1, "G104", "北京-平潭", null);
        insertCapacity(1, "S999", "不存在路线", 1920, 100, 0.1, null);

        assertRefreshing();
    }

    @Test
    void allowsAnEmptyCurrentCapacityBatch() {
        insertRoute(1, "G104", "北京-平潭", null);

        assertEquals(0, source.loadCandidate().capacities().size());
    }

    @Test
    void rejectsNullAndNegativeValuesButAllowsOverCapacityUtilization() {
        insertRoute(1, "G104", "北京-平潭", null);
        jdbc.update("""
                INSERT INTO w_road_capacity
                VALUES (1, 'G104', '北京-平潭', 1920, NULL, 0.5, NULL, CURRENT_TIMESTAMP, NULL)
                """);
        assertRefreshing();

        jdbc.update("UPDATE w_road_capacity SET avg_previous_hour=-1 WHERE id=1");
        assertUnavailable();

        jdbc.update("UPDATE w_road_capacity SET avg_previous_hour=10, utilization_perc=-0.01 WHERE id=1");
        assertUnavailable();

        jdbc.update("UPDATE w_road_capacity SET utilization_perc=1.01 WHERE id=1");
        assertEquals(CapacityLevel.SEVERE_BOTTLENECK, source.loadCandidate().capacities().get(0).level());
    }

    private void assertRefreshing() {
        assertEquals("ROAD_CAPACITY_DATA_REFRESHING", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    private void assertUnavailable() {
        assertEquals("ROAD_CAPACITY_DATA_UNAVAILABLE", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    private void insertRoute(long id, String code, String name, String delFlag) {
        jdbc.update("INSERT INTO w_highway_network VALUES (?, ?, ?, '国道', '宁德市', '福州市', ?)",
                id, code, name, delFlag);
    }

    private void insertCapacity(
            long id, String code, String name, double design, double actual, double ratio, String delFlag
    ) {
        jdbc.update("""
                INSERT INTO w_road_capacity
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """, id, code, name, design, actual, ratio, delFlag);
    }
}
