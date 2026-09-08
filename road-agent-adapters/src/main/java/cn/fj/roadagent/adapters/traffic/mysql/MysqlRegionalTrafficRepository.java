package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.RegionalConnectionHub;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 需求1-5即时读取路网关系和同路线卡口；不使用卡口region_code。 */
public final class MysqlRegionalTrafficRepository implements RegionalTrafficDataPort {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MysqlRegionalTrafficRepository(JdbcTemplate jdbcTemplate, TransactionTemplate transactionTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        transactionTemplate.setReadOnly(true);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public RegionalTrafficSnapshot load() {
        try {
            RegionalTrafficSnapshot snapshot = transactionTemplate.execute(ignored -> loadInTransaction());
            if (snapshot == null) throw new IllegalStateException("区域交通查询事务未返回结果");
            return snapshot;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ExternalServiceException("MYSQL_REGIONAL_TRAFFIC", "REGIONAL_TRAFFIC_DATA_INVALID",
                    "区域交通数据校验失败：" + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("MYSQL_REGIONAL_TRAFFIC", "REGIONAL_TRAFFIC_DATA_UNAVAILABLE",
                    "区域交通数据库暂时不可用", exception);
        }
    }

    private RegionalTrafficSnapshot loadInTransaction() {
        List<RouteRow> routes = jdbcTemplate.query("""
                SELECT route_code, route_name, start_place, end_place
                FROM w_highway_network
                WHERE del_flag IS NULL OR del_flag IN ('N', '0')
                ORDER BY route_code
                """, (rs, rowNum) -> new RouteRow(rs.getString("route_code"), rs.getString("route_name"),
                rs.getString("start_place"), rs.getString("end_place")));
        if (routes.isEmpty()) throw new IllegalArgumentException("没有可用路网记录");

        Map<String, CrossCityRoute> crossCityRoutes = new HashMap<>();
        Set<String> routeCodes = new HashSet<>();
        int invalidRoutes = 0;
        for (RouteRow row : routes) {
            String routeCode = trim(row.routeCode());
            if (routeCode.isEmpty()) {
                invalidRoutes++;
                continue;
            }
            if (!routeCodes.add(routeCode)) throw new IllegalArgumentException("活动路线编号重复：" + routeCode);
            var start = FujianCity.fromName(row.startPlace());
            var end = FujianCity.fromName(row.endPlace());
            if (start.isEmpty() || end.isEmpty() || trim(row.routeName()).isEmpty()) {
                invalidRoutes++;
                continue;
            }
            if (start.get() == end.get()) {
                continue;
            }
            FujianCity first = start.get().adcode().compareTo(end.get().adcode()) <= 0 ? start.get() : end.get();
            FujianCity second = first == start.get() ? end.get() : start.get();
            crossCityRoutes.put(routeCode, new CrossCityRoute(routeCode, required(row.routeName(), "路线名称"), first, second));
        }

        List<HubRow> rawHubs = jdbcTemplate.query("""
                SELECT checkpoint_no, checkpoint_name, route_code, route_name, stake,
                       average_speed, daily_avg_flow, temp_2
                FROM w_transport_hubs
                WHERE del_flag IS NULL OR del_flag IN ('N', '0')
                ORDER BY checkpoint_no
                """, (rs, rowNum) -> new HubRow(
                rs.getString("checkpoint_no"), rs.getString("checkpoint_name"), rs.getString("route_code"),
                rs.getString("route_name"), rs.getObject("stake"), rs.getObject("average_speed"),
                rs.getObject("daily_avg_flow"), rs.getObject("temp_2")));
        if (rawHubs.isEmpty()) throw new IllegalArgumentException("没有可用卡口记录");

        Set<String> checkpoints = new HashSet<>();
        List<RegionalConnectionHub> hubs = new ArrayList<>();
        int skippedHubs = 0;
        int routeNameOverrides = 0;
        for (HubRow row : rawHubs) {
            String checkpoint = trim(row.checkpointNo());
            if (checkpoint.isEmpty()) {
                skippedHubs++;
                continue;
            }
            if (!checkpoints.add(checkpoint)) throw new IllegalArgumentException("活动卡口编号重复：" + checkpoint);
            CrossCityRoute route = crossCityRoutes.get(trim(row.routeCode()));
            if (route == null) {
                skippedHubs++;
                continue;
            }
            try {
                if (!normalize(row.routeName()).equals(normalize(route.routeName()))) routeNameOverrides++;
                hubs.add(new RegionalConnectionHub(checkpoint, row.checkpointName(), route.routeCode(), route.routeName(),
                        route.cityA().adcode(), route.cityA().displayName() + "市",
                        route.cityB().adcode(), route.cityB().displayName() + "市",
                        optionalDouble(row.stake(), "桩号"), requiredDouble(row.averageSpeed(), "卡口均速"),
                        requiredLong(row.weeklyFlow(), "卡口7日总流量"), requiredLong(row.dailyFlow(), "卡口日均流量")));
            } catch (IllegalArgumentException ignored) {
                skippedHubs++;
            }
        }
        if (hubs.isEmpty()) throw new IllegalArgumentException("路网与卡口未形成有效跨城市路线关系");

        List<String> warnings = new ArrayList<>();
        if (invalidRoutes > 0) warnings.add("已跳过" + invalidRoutes + "条起终点或名称不合法的路网记录。");
        if (skippedHubs > 0) warnings.add("已跳过" + skippedHubs + "条不属于有效跨市路线或数值不合法的卡口记录。");
        if (routeNameOverrides > 0) warnings.add("有" + routeNameOverrides + "条卡口路线名称与路网不一致，已采用路网权威名称。");
        Timestamp timestamp = jdbcTemplate.queryForObject("""
                SELECT MAX(COALESCE(update_time, create_time)) FROM w_transport_hubs
                WHERE del_flag IS NULL OR del_flag IN ('N', '0')
                """, Timestamp.class);
        Instant acquiredAt = timestamp == null ? clock.instant() : timestamp.toInstant();
        return new RegionalTrafficSnapshot(hubs, acquiredAt, warnings);
    }

    private static String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String normalize(String value) { return trim(value).replaceAll("[\\p{Pd}\\s]", "").toLowerCase(java.util.Locale.ROOT); }

    private static double requiredDouble(Object value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        try {
            double result = value instanceof Number number ? number.doubleValue() : Double.parseDouble(value.toString().trim());
            if (!Double.isFinite(result) || result < 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) { throw new IllegalArgumentException(label + "必须是非负数值"); }
    }
    private static Double optionalDouble(Object value, String label) {
        return value == null || value.toString().isBlank() ? null : requiredDouble(value, label);
    }
    private static long requiredLong(Object value, String label) {
        if (value == null) throw new IllegalArgumentException(label + "不能为空");
        try {
            long result = new java.math.BigDecimal(value.toString().trim()).longValueExact();
            if (result < 0) throw new ArithmeticException();
            return result;
        } catch (RuntimeException exception) { throw new IllegalArgumentException(label + "必须是非负整数"); }
    }

    private record RouteRow(String routeCode, String routeName, String startPlace, String endPlace) { }
    private record CrossCityRoute(String routeCode, String routeName, FujianCity cityA, FujianCity cityB) { }
    private record HubRow(String checkpointNo, String checkpointName, String routeCode, String routeName,
                          Object stake, Object averageSpeed, Object dailyFlow, Object weeklyFlow) { }
}
