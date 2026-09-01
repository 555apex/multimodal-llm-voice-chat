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
                CREATE TABLE w_region_code (
                  id BIGINT PRIMARY KEY, code VARCHAR(6), name VARCHAR(50), del_flag VARCHAR(2)
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_transport_hubs (
                  id BIGINT PRIMARY KEY, checkpoint_no VARCHAR(100), route_code VARCHAR(20),
                  route_name VARCHAR(100), average_speed DECIMAL(10,2), region_code VARCHAR(6),
                  daily_avg_flow BIGINT, del_flag VARCHAR(2), create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        repository = new MysqlRegionalTrafficRepository(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), Clock.systemUTC()
        );
        jdbc.update("INSERT INTO w_region_code VALUES (1, '350100', '福州市', 'N')");
        jdbc.update("INSERT INTO w_region_code VALUES (2, '350200', '厦门市', 'N')");
    }

    @Test
    void loadsMappedActiveRowsAndIgnoresLogicalDeletion() {
        insertHub(1, "F001", "G104", "北京-平潭", 30, "350100", 101, "N");
        insertHub(2, "X001", "S201", "柘荣-霞浦", 20, "350200", 200, "Y");

        var snapshot = repository.load();

        assertEquals(1, snapshot.hubs().size());
        assertEquals("福州市", snapshot.hubs().get(0).regionName());
        assertEquals(101, snapshot.hubs().get(0).dailyAverageFlow());
    }

    @Test
    void rejectsDuplicateCheckpointInvalidRegionNegativeAndRouteNameConflict() {
        insertHub(1, "F001", "G104", "北京-平潭", 30, "350100", 101, "N");
        insertHub(2, "F001", "G104", "北京-平潭", 30, "350100", 101, "N");
        assertInvalid();

        jdbc.update("DELETE FROM w_transport_hubs WHERE id=2");
        jdbc.update("UPDATE w_transport_hubs SET region_code='359999' WHERE id=1");
        assertInvalid();

        jdbc.update("UPDATE w_transport_hubs SET region_code='350100', daily_avg_flow=-1 WHERE id=1");
        assertInvalid();

        jdbc.update("UPDATE w_transport_hubs SET daily_avg_flow=1 WHERE id=1");
        insertHub(3, "F003", "G104", "错误名称", 30, "350100", 1, "N");
        assertInvalid();
    }

    private void assertInvalid() {
        assertEquals("REGIONAL_TRAFFIC_DATA_INVALID", assertThrows(
                ExternalServiceException.class, repository::load
        ).errorCode());
    }

    private void insertHub(
            long id, String checkpoint, String route, String name, double speed,
            String region, long flow, String delFlag
    ) {
        jdbc.update("""
                INSERT INTO w_transport_hubs
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """, id, checkpoint, route, name, speed, region, flow, delFlag);
    }
}
