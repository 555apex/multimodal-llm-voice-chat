package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.domain.traffic.VehicleType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MysqlVehicleTravelPatternRepositoryTest {
    private JdbcTemplate jdbc;
    private MysqlVehicleTravelPatternRepository repository;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:vehicle;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_vehicletravelpatternanalyzer (
                  id BIGINT PRIMARY KEY, cityName VARCHAR(255), result1 VARCHAR(2000),
                  result2 VARCHAR(8000), result3 VARCHAR(2000), del_flag VARCHAR(2),
                  create_time TIMESTAMP
                )
                """);
        repository = new MysqlVehicleTravelPatternRepository(
                jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)), new ObjectMapper()
        );
    }

    @Test
    void selectsLatestRowPerCityAndUsesIdAsTieBreaker() {
        insert(1, "福州市", 100, "2026-08-27 15:00:00", "N");
        insert(2, "厦门市", 900, "2026-08-27 16:00:00", "N");
        insert(3, "福州市", 200, "2026-08-27 16:00:00", "N");
        insert(4, "福州市", 300, "2026-08-27 16:00:00", "N");

        var fuzhou = repository.latestForCity("福州").orElseThrow();
        var xiamen = repository.latestForCity("厦门市").orElseThrow();

        assertEquals(300, fuzhou.weeklyVolume(VehicleType.CAR));
        assertEquals(900, xiamen.weeklyVolume(VehicleType.CAR));
        assertEquals(1, fuzhou.hourlyFlows().size());
        assertEquals(0, fuzhou.hourlyFlows().get(0).volume(VehicleType.TRUCK));
    }

    @Test
    void ignoresDeletedRowsAndRejectsMalformedOrImpossibleData() {
        insert(1, "福州市", 100, "2026-08-27 16:00:00", "Y");
        assertEquals(true, repository.latestForCity("福州").isEmpty());

        jdbc.update("""
                INSERT INTO w_vehicletravelpatternanalyzer
                VALUES (2, '福州市', '{bad}', '{}', '{"car":1,"bus":1,"truck":1}', 'N', TIMESTAMP '2026-08-27 16:00:01')
                """);
        assertInvalid();

        jdbc.update("UPDATE w_vehicletravelpatternanalyzer SET result1='{\"car\":1,\"bus\":1,\"truck\":1}', result3='{\"car\":2,\"bus\":1,\"truck\":1}' WHERE id=2");
        assertInvalid();
    }

    private void assertInvalid() {
        assertEquals("VEHICLE_PATTERN_DATA_INVALID", assertThrows(
                ExternalServiceException.class, () -> repository.latestForCity("福州")
        ).errorCode());
    }

    private void insert(long id, String city, long car, String time, String delFlag) {
        String result1 = "{\"car\":" + car + ",\"bus\":20,\"truck\":10}";
        String result2 = "{\"2026-08-27 10:00:00\":{\"car\":11,\"bus\":2}}";
        String result3 = "{\"car\":10,\"bus\":2,\"truck\":1}";
        jdbc.update("""
                INSERT INTO w_vehicletravelpatternanalyzer
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, id, city, result1, result2, result3, delFlag, time);
    }
}
