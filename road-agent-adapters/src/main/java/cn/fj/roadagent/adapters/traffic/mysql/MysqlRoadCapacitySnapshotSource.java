package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.RoadCapacitySnapshotSource;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.RoadCapacity;
import cn.fj.roadagent.domain.traffic.RoadCapacitySnapshot;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 在独立只读一致性事务中读取并校验 w_road_capacity。 */
public final class MysqlRoadCapacitySnapshotSource implements RoadCapacitySnapshotSource {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MysqlRoadCapacitySnapshotSource(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
        this.transactionTemplate.setReadOnly(true);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public RoadCapacitySnapshot loadCandidate() {
        try {
            RoadCapacitySnapshot result = transactionTemplate.execute(status -> loadInTransaction());
            if (result == null) {
                throw new IllegalStateException("读取道路通行能力快照失败");
            }
            return result;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(
                    "MYSQL_ROAD_CAPACITY", "ROAD_CAPACITY_DATA_UNAVAILABLE",
                    "道路通行能力数据库暂时不可用", exception
            );
        }
    }

    private RoadCapacitySnapshot loadInTransaction() {
        List<HighwayRoute> routes = loadRoutes();
        List<RoadCapacity> capacities = loadCapacities();
        validate(routes, capacities);
        return new RoadCapacitySnapshot(capacities, loadAcquiredAt(), fingerprint(routes, capacities));
    }

    private List<HighwayRoute> loadRoutes() {
        return jdbcTemplate.query("""
                SELECT route_code, route_name, road_type, start_place, end_place
                FROM w_highway_network
                WHERE (del_flag IS NULL OR del_flag IN ('N', '0'))
                  AND (route_code LIKE 'G%' OR route_code LIKE 'S%')
                ORDER BY route_code
                """, (rs, rowNum) -> new HighwayRoute(
                rs.getString("route_code"), rs.getString("route_name"), rs.getString("road_type"),
                rs.getString("start_place"), rs.getString("end_place")
        ));
    }

    private List<RoadCapacity> loadCapacities() {
        return jdbcTemplate.query("""
                SELECT route_code, route_name, design_flow, avg_previous_hour, utilization_perc
                FROM w_road_capacity
                WHERE (del_flag IS NULL OR del_flag IN ('N', '0'))
                  AND (route_code LIKE 'G%' OR route_code LIKE 'S%')
                ORDER BY route_code
                """, (rs, rowNum) -> new RoadCapacity(
                rs.getString("route_code"), rs.getString("route_name"),
                requiredDouble(rs.getObject("avg_previous_hour"), "实际通行能力"),
                requiredDouble(rs.getObject("design_flow"), "设计通行能力"),
                requiredDouble(rs.getObject("utilization_perc"), "通行能力利用率")
        ));
    }

    private Instant loadAcquiredAt() {
        Timestamp timestamp = jdbcTemplate.queryForObject("""
                SELECT MAX(COALESCE(update_time, create_time))
                FROM w_road_capacity
                WHERE (del_flag IS NULL OR del_flag IN ('N', '0'))
                  AND (route_code LIKE 'G%' OR route_code LIKE 'S%')
                """, Timestamp.class);
        return timestamp == null ? clock.instant() : timestamp.toInstant();
    }

    private String fingerprint(List<HighwayRoute> routes, List<RoadCapacity> capacities) {
        List<String> rows = new ArrayList<>();
        routes.forEach(route -> rows.add(String.join("\u001f", "N",
                text(route.routeCode()), text(route.routeName()), text(route.roadType()),
                text(route.startPlace()), text(route.endPlace()))));
        capacities.forEach(capacity -> rows.add(String.join("\u001f", "C",
                text(capacity.routeCode()), text(capacity.routeName()),
                text(capacity.designCapacityVph()), text(capacity.actualCapacityVph()),
                text(capacity.utilizationRatio()))));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (String row : rows) {
                digest.update(row.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM不支持SHA-256", exception);
        }
    }

    private void validate(List<HighwayRoute> routes, List<RoadCapacity> capacities) {
        if (routes.isEmpty()) {
            throw refreshing("国省道路网尚未加载完成");
        }
        Map<String, HighwayRoute> routeMap = uniqueBy(routes, HighwayRoute::routeCode, "路网路线编号重复");
        Map<String, RoadCapacity> capacityMap = uniqueBy(
                capacities, RoadCapacity::routeCode, "通行能力路线编号重复"
        );
        if (!routeMap.keySet().containsAll(capacityMap.keySet())) {
            throw refreshing("通行能力数据包含非活动国省道路线");
        }
        for (String routeCode : capacityMap.keySet()) {
            if (!Objects.equals(
                    normalizeName(routeMap.get(routeCode).routeName()),
                    normalizeName(capacityMap.get(routeCode).routeName())
            )) {
                throw refreshing("路线名称跨表不一致：" + routeCode);
            }
        }
    }

    private <T> Map<String, T> uniqueBy(
            List<T> values,
            Function<T, String> keyFunction,
            String duplicateMessage
    ) {
        return Map.copyOf(values.stream().collect(Collectors.toMap(
                keyFunction, Function.identity(), (left, right) -> {
                    throw refreshing(duplicateMessage);
                }
        )));
    }

    private double requiredDouble(Object value, String label) {
        if (value == null) {
            throw refreshing(label + "存在空值");
        }
        return ((Number) value).doubleValue();
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Pd}\\s]", "");
    }

    private String text(Object value) {
        return value == null ? "<NULL>" : String.valueOf(value);
    }

    private ExternalServiceException refreshing(String detail) {
        return new ExternalServiceException(
                "MYSQL_ROAD_CAPACITY", "ROAD_CAPACITY_DATA_REFRESHING",
                "道路通行能力数据正在更新，请稍后重试（" + detail + "）"
        );
    }
}
