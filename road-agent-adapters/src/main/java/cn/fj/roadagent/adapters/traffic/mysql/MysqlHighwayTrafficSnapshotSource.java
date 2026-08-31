package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.HighwayTrafficSnapshotSource;
import cn.fj.roadagent.domain.traffic.HighwayRoute;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSegment;
import cn.fj.roadagent.domain.traffic.HighwayTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.RouteTrafficSummary;
import cn.fj.roadagent.domain.traffic.TrafficStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 在只读一致性事务中读取并验证三张交通业务表。 */
public final class MysqlHighwayTrafficSnapshotSource implements HighwayTrafficSnapshotSource {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MysqlHighwayTrafficSnapshotSource(
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
    public HighwayTrafficSnapshot loadCandidate() {
        try {
            HighwayTrafficSnapshot result = transactionTemplate.execute(status -> loadInTransaction());
            if (result == null) {
                throw new IllegalStateException("读取交通快照失败");
            }
            return result;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(
                    "MYSQL_TRAFFIC", "TRAFFIC_DATA_UNAVAILABLE", "交通数据库暂时不可用", exception
            );
        }
    }

    private HighwayTrafficSnapshot loadInTransaction() {
        List<HighwayRoute> routes = loadRoutes();
        List<RouteTrafficSummary> summaries = loadRouteSummaries();
        List<HighwayTrafficSegment> segments = loadSegments();
        Instant acquiredAt = loadAcquiredAt();
        validate(routes, summaries, segments);
        return new HighwayTrafficSnapshot(
                routes, summaries, segments, acquiredAt, fingerprint(routes, summaries, segments)
        );
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

    private List<RouteTrafficSummary> loadRouteSummaries() {
        return jdbcTemplate.query("""
                SELECT rout_code, rout_name, uniform_speed, status
                FROM w_road_network_status
                WHERE del_flag IS NULL OR del_flag IN ('N', '0')
                  AND (rout_code LIKE 'G%' OR rout_code LIKE 'S%')
                ORDER BY rout_code
                """, (rs, rowNum) -> new RouteTrafficSummary(
                rs.getString("rout_code"), rs.getString("rout_name"),
                nullableDouble(rs.getObject("uniform_speed")),
                TrafficStatus.fromDatabase(rs.getString("status"))
        ));
    }

    private List<HighwayTrafficSegment> loadSegments() {
        return jdbcTemplate.query("""
                SELECT rout_code, rout_name, rout_section, distance, uniform_speed, status, severity
                FROM w_congestion_detection_result
                WHERE del_flag IS NULL OR del_flag IN ('N', '0')
                  AND (rout_code LIKE 'G%' OR rout_code LIKE 'S%')
                ORDER BY rout_code, rout_section
                """, (rs, rowNum) -> new HighwayTrafficSegment(
                rs.getString("rout_code"), rs.getString("rout_name"), rs.getString("rout_section"),
                nullableDouble(rs.getObject("distance")), nullableDouble(rs.getObject("uniform_speed")),
                TrafficStatus.fromDatabase(rs.getString("status")),
                nullableDouble(rs.getObject("severity"))
        ));
    }

    private Instant loadAcquiredAt() {
        Timestamp timestamp = jdbcTemplate.queryForObject("""
                SELECT GREATEST(
                    COALESCE((SELECT MAX(COALESCE(update_time, create_time)) FROM w_road_network_status
                              WHERE del_flag IS NULL OR del_flag IN ('N', '0')), '1970-01-01'),
                    COALESCE((SELECT MAX(COALESCE(update_time, create_time)) FROM w_congestion_detection_result
                              WHERE del_flag IS NULL OR del_flag IN ('N', '0')), '1970-01-01')
                )
                """, Timestamp.class);
        return timestamp == null || timestamp.toInstant().equals(Instant.EPOCH)
                ? clock.instant() : timestamp.toInstant();
    }

    private String fingerprint(
            List<HighwayRoute> routes,
            List<RouteTrafficSummary> summaries,
            List<HighwayTrafficSegment> segments
    ) {
        List<String> rows = new java.util.ArrayList<>();
        routes.forEach(route -> rows.add(String.join("\u001f", "N",
                text(route.routeCode()), text(route.routeName()), text(route.roadType()),
                text(route.startPlace()), text(route.endPlace()))));
        summaries.forEach(summary -> rows.add(String.join("\u001f", "R",
                text(summary.routeCode()), text(summary.routeName()), text(summary.averageSpeedKmh()),
                String.valueOf(summary.status().code()))));
        segments.forEach(segment -> rows.add(String.join("\u001f", "S",
                text(segment.routeCode()), text(segment.routeName()), text(segment.routeSection()),
                text(segment.distanceKm()), text(segment.averageSpeedKmh()),
                String.valueOf(segment.status().code()), text(segment.severity()))));
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

    private String text(Object value) {
        return value == null ? "<NULL>" : String.valueOf(value);
    }

    private void validate(
            List<HighwayRoute> routes,
            List<RouteTrafficSummary> summaries,
            List<HighwayTrafficSegment> segments
    ) {
        if (routes.isEmpty()) {
            throw refreshing("国省道路网尚未加载完成");
        }
        Map<String, HighwayRoute> routeMap = uniqueBy(routes, HighwayRoute::routeCode, "路网路线编号重复");
        Map<String, RouteTrafficSummary> summaryMap = uniqueBy(
                summaries, RouteTrafficSummary::routeCode, "路线状态编号重复"
        );
        Set<String> segmentKeys = new HashSet<>();
        for (HighwayTrafficSegment segment : segments) {
            if (!segmentKeys.add(segment.routeCode() + "|" + segment.routeSection())) {
                throw refreshing("路段自然键重复");
            }
        }
        Set<String> routeCodes = routeMap.keySet();
        if (!routeCodes.containsAll(summaryMap.keySet())) {
            throw refreshing("路线整体状态包含非活动国省道路线");
        }
        Set<String> segmentRouteCodes = segments.stream()
                .map(HighwayTrafficSegment::routeCode).collect(Collectors.toSet());
        if (!routeCodes.containsAll(segmentRouteCodes)) {
            throw refreshing("路段状态包含非活动国省道路线");
        }
        for (String routeCode : summaryMap.keySet()) {
            HighwayRoute route = routeMap.get(routeCode);
            RouteTrafficSummary summary = summaryMap.get(routeCode);
            if (!Objects.equals(normalizeName(route.routeName()), normalizeName(summary.routeName()))) {
                throw refreshing("路线名称跨表不一致：" + routeCode);
            }
        }
        for (HighwayTrafficSegment segment : segments) {
            HighwayRoute route = routeMap.get(segment.routeCode());
            if (!Objects.equals(normalizeName(route.routeName()), normalizeName(segment.routeName()))) {
                throw refreshing("路段路线名称跨表不一致：" + segment.routeCode());
            }
        }
    }

    private <T> Map<String, T> uniqueBy(
            List<T> values,
            Function<T, String> keyFunction,
            String duplicateMessage
    ) {
        Map<String, T> result = values.stream().collect(Collectors.toMap(
                keyFunction, Function.identity(), (left, right) -> {
                    throw refreshing(duplicateMessage);
                }
        ));
        return Map.copyOf(result);
    }

    private String normalizeName(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Pd}\\s]", "");
    }

    private Double nullableDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    private ExternalServiceException refreshing(String detail) {
        return new ExternalServiceException(
                "MYSQL_TRAFFIC", "TRAFFIC_DATA_REFRESHING", "交通数据正在更新，请稍后重试（" + detail + "）"
        );
    }
}
