package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.traffic.mysql.MysqlRegionalTrafficRepository;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.traffic.OdTrafficService;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 不启动应用、不写数据；校验需求1-7直接复用跨市路线与卡口事实。 */
@EnabledIfEnvironmentVariable(named = "ROADAGENT_DB_URL", matches = ".+")
class OdTrafficReadOnlyIntegrationTest {
    @Test void verifiesDestinationTendencyAndMatrixAgainstSharedMysql() {
        var ds = new DriverManagerDataSource(System.getenv("ROADAGENT_DB_URL"),
                System.getenv("ROADAGENT_DB_USERNAME"), System.getenv("ROADAGENT_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        var manager = new DataSourceTransactionManager(ds);
        var repository = new MysqlRegionalTrafficRepository(jdbc, new TransactionTemplate(manager), Clock.systemUTC());
        var service = new OdTrafficService(repository, null);
        var single = service.collectFacts(new HighwayTrafficQuery(TrafficQueryType.OD_DESTINATION_TENDENCY,
                null, null, null, null, List.of("福州"), null, "readonly-od-single"));
        assertFalse(single.destinationRows().isEmpty());
        assertEquals(1.0, single.destinationRows().stream().mapToDouble(row -> row.tendencyRatio()).sum(), 0.00001);
        assertTrue(single.destinationRows().stream().allMatch(row -> row.routeCount() > 0
                && row.weeklyConnectionStrength() >= 0 && row.tendencyRatio() >= 0 && row.tendencyRatio() <= 1));

        var matrix = service.collectFacts(new HighwayTrafficQuery(TrafficQueryType.OD_CONNECTION_MATRIX,
                null, null, null, null, List.of("福州", "厦门"), null, "readonly-od-matrix"));
        assertEquals(2, matrix.matrixRows().size());
        var singleXiamen = single.destinationRows().stream().filter(row -> row.destinationCityName().equals("厦门市"))
                .findFirst().orElseThrow();
        var matrixXiamen = matrix.matrixRows().get(0).cells().get(1);
        assertEquals(singleXiamen.weeklyConnectionStrength(), matrixXiamen.weeklyConnectionStrength());
        assertEquals(singleXiamen.tendencyRatio(), matrixXiamen.tendencyRatio());
    }
}
