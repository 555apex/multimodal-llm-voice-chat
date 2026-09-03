package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.traffic.mysql.MysqlOdTrafficRepository;
import cn.fj.roadagent.application.traffic.HighwayTrafficQuery;
import cn.fj.roadagent.core.traffic.OdTrafficService;
import cn.fj.roadagent.domain.traffic.TrafficQueryType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** 不启动应用、不加载应急流程、不写数据；校验和参考SQL处于同一只读一致性事务。 */
@EnabledIfEnvironmentVariable(named = "ROADAGENT_DB_URL", matches = ".+")
class OdTrafficReadOnlyIntegrationTest {
    @Test void verifiesCityUnionWeeklyDailyAndVehicleAggregatesAgainstSharedMysql() {
        var ds = new DriverManagerDataSource(System.getenv("ROADAGENT_DB_URL"),
                System.getenv("ROADAGENT_DB_USERNAME"), System.getenv("ROADAGENT_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(ds);
        var manager = new DataSourceTransactionManager(ds);
        var transaction = new TransactionTemplate(manager);
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        var repository = new MysqlOdTrafficRepository(jdbc, new TransactionTemplate(manager), new ObjectMapper(), Clock.systemUTC());
        var service = new OdTrafficService(repository, null);
        transaction.executeWithoutResult(status -> {
            for (var cities : List.of(List.of("福州", "厦门"), List.of("福州", "厦门", "泉州"), List.<String>of())) {
                var facts = service.collectFacts(new HighwayTrafficQuery(
                        TrafficQueryType.OD_OVERVIEW, null, null, null, null, cities, null, "readonly-od"));
                var codes = facts.selectedRegions().stream().map(r -> r.regionCode()).toList();
                String where = " WHERE (del_flag IS NULL OR del_flag IN ('N','0')) AND region_code IN ("
                        + String.join(",", java.util.Collections.nCopies(codes.size(), "?")) + ")";
                var expected = jdbc.queryForMap("SELECT COUNT(*) AS hubs,SUM(CAST(temp_2 AS DECIMAL(30,0))) AS weekly,"
                        + "SUM(daily_avg_flow) AS daily FROM w_transport_hubs" + where, codes.toArray());
                assertEquals(((Number) expected.get("hubs")).intValue(), facts.checkpointCount());
                assertEquals(((Number) expected.get("weekly")).longValue(), facts.weeklyTotalFlow());
                assertEquals(((Number) expected.get("daily")).longValue(), facts.dailyAverageFlow());
                assertEquals(facts.weeklyTotalFlow(), facts.cityRows().stream().mapToLong(r -> r.weeklyTotalFlow()).sum());
                assertEquals(facts.weeklyTotalFlow(), facts.channelRows().stream().mapToLong(r -> r.weeklyTotalFlow()).sum());
                for (var row : facts.channelRows()) {
                    var args = new java.util.ArrayList<Object>(codes);
                    args.add(row.routeCode());
                    long car = jdbc.queryForObject("SELECT SUM(CAST(JSON_UNQUOTE(JSON_EXTRACT(temp_3,'$.car')) AS DECIMAL(30,0)))"
                            + " FROM w_transport_hubs" + where + " AND route_code=?", Long.class, args.toArray());
                    assertEquals(car, row.carWeeklyFlow());
                }
            }
        });
    }
}
