package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.domain.traffic.TrafficStatus;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlHighwayTrafficSnapshotSourceTest {
    private JdbcTemplate jdbc;
    private MysqlHighwayTrafficSnapshotSource source;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:traffic;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_highway_network (
                  id BIGINT PRIMARY KEY, route_code VARCHAR(20), route_name VARCHAR(100),
                  road_type VARCHAR(20), start_place VARCHAR(50), end_place VARCHAR(50),
                  del_flag VARCHAR(2)
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_road_network_status (
                  id BIGINT PRIMARY KEY, rout_code VARCHAR(20), rout_name VARCHAR(100),
                  uniform_speed DECIMAL(10,2), status VARCHAR(10), del_flag VARCHAR(2),
                  create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_congestion_detection_result (
                  id BIGINT PRIMARY KEY, rout_code VARCHAR(20), rout_name VARCHAR(100),
                  rout_section VARCHAR(100), distance DECIMAL(10,2), uniform_speed DECIMAL(10,2),
                  status VARCHAR(10), severity DECIMAL(10,4), del_flag VARCHAR(2),
                  create_time TIMESTAMP, update_time TIMESTAMP
                )
                """);
        source = new MysqlHighwayTrafficSnapshotSource(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), Clock.systemUTC()
        );
    }

    @Test
    void loadsFiveStatusesAndIgnoresLogicallyDeletedRows() {
        int[] codes = {10, 20, 30, 40, 50};
        for (int index = 0; index < codes.length; index++) {
            String route = "G10" + index;
            insertRoute(index + 1, route, "路线" + index, null);
            insertSummary(index + 1, route, "路线" + index, codes[index], null);
            insertSegment(index + 1, route, "路线" + index, "FJ" + index, codes[index], 0.1 + index * 0.2, null);
        }
        insertRoute(99, "G999", "已删除", "1");
        insertSummary(99, "G999", "已删除", 99, "1");
        insertSegment(99, "G999", "已删除", "deleted", 99, 9.9, "1");

        var snapshot = source.loadCandidate();

        assertEquals(5, snapshot.routes().size());
        assertEquals(5, snapshot.routeSummaries().size());
        assertEquals(5, snapshot.segments().size());
        assertEquals(TrafficStatus.BLOCKED, snapshot.segments().get(4).status());
    }

    @Test
    void allowsPartialAndIndependentBusinessCoverageButRejectsDuplicateSegmentNaturalKey() {
        insertRoute(1, "G104", "北京-平潭", null);
        insertRoute(2, "S201", "柘荣-霞浦", null);
        insertSummary(1, "G104", "北京-平潭", 10, null);

        var partial = source.loadCandidate();
        assertEquals(2, partial.routes().size());
        assertEquals(1, partial.routeSummaries().size());
        assertEquals(0, partial.segments().size());

        insertSegment(1, "G104", "北京-平潭", "FJ001", 10, 0.1, null);
        insertSegment(2, "G104", "北京-平潭", "FJ001", 20, 0.3, null);
        assertEquals("TRAFFIC_DATA_REFRESHING", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    @Test
    void rejectsTrafficRowsOutsideActiveNetwork() {
        insertRoute(1, "G104", "北京-平潭", null);
        insertSummary(1, "S999", "不存在路线", 10, null);

        assertEquals("TRAFFIC_DATA_REFRESHING", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    @Test
    void rejectsIllegalActiveStatusAndNumericRange() {
        insertRoute(1, "S201", "柘荣-霞浦", "N");
        insertSummary(1, "S201", "柘荣-霞浦", 25, "0");
        insertSegment(1, "S201", "柘荣-霞浦", "FJ001", 10, 1.2, null);

        assertEquals("TRAFFIC_DATA_UNAVAILABLE", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    @Test
    void ignoresNonGsRowsEvenWhenRoadTypeTextLooksLikeHighway() {
        insertRoute(1, "X001", "非国省道编号", null);
        insertSummary(1, "X001", "非国省道编号", 10, null);
        insertSegment(1, "X001", "非国省道编号", "FJ001", 10, 0.1, null);

        assertEquals("TRAFFIC_DATA_REFRESHING", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    @Test
    void rejectsNullRequiredTrafficValues() {
        insertRoute(1, "G104", "北京-平潭", null);
        jdbc.update("""
                INSERT INTO w_road_network_status
                VALUES (1, 'G104', '北京-平潭', NULL, '10', NULL, CURRENT_TIMESTAMP, NULL)
                """);
        insertSegment(1, "G104", "北京-平潭", "FJ001", 10, 0.1, null);

        assertEquals("TRAFFIC_DATA_UNAVAILABLE", assertThrows(
                ExternalServiceException.class, source::loadCandidate
        ).errorCode());
    }

    private void insertRoute(long id, String code, String name, String delFlag) {
        jdbc.update("INSERT INTO w_highway_network VALUES (?, ?, ?, '国道', '宁德市', '福州市', ?)",
                id, code, name, delFlag);
    }

    private void insertSummary(long id, String code, String name, int status, String delFlag) {
        jdbc.update("""
                INSERT INTO w_road_network_status
                VALUES (?, ?, ?, 60, ?, ?, CURRENT_TIMESTAMP, NULL)
                """, id, code, name, String.valueOf(status), delFlag);
    }

    private void insertSegment(
            long id, String code, String name, String section, int status, double severity, String delFlag
    ) {
        jdbc.update("""
                INSERT INTO w_congestion_detection_result
                VALUES (?, ?, ?, ?, 10, 40, ?, ?, ?, CURRENT_TIMESTAMP, NULL)
                """, id, code, name, section, String.valueOf(status), severity, delFlag);
    }
}
