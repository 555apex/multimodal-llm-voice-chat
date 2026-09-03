package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.OdTrafficDataPort;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.OdTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.OdTransportHub;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

/** 独立的按请求只读查询；不要求完整九市、固定路线集合或相同更新时间。 */
public final class MysqlOdTrafficRepository implements OdTrafficDataPort {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final ObjectMapper mapper;
    private final Clock clock;

    public MysqlOdTrafficRepository(JdbcTemplate jdbc, TransactionTemplate transaction,
                                    ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
        transaction.setReadOnly(true);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public OdTrafficSnapshot load(List<String> regionCodes, boolean includeVehicles) {
        try {
            if (regionCodes.isEmpty() || regionCodes.stream().anyMatch(c -> FujianCity.fromAdcode(c).isEmpty())) {
                throw new IllegalArgumentException("请选择有效的福建地级市");
            }
            return Objects.requireNonNull(transaction.execute(status -> read(regionCodes, includeVehicles)));
        } catch (IllegalArgumentException | ArithmeticException exception) {
            throw new ExternalServiceException("MYSQL_OD_TRAFFIC", "OD_ANALYSIS_DATA_INVALID",
                    "OD统计数据校验失败：" + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            throw new ExternalServiceException("MYSQL_OD_TRAFFIC", "OD_ANALYSIS_DATA_UNAVAILABLE",
                    "城市OD统计数据库暂时不可用", exception);
        }
    }

    private OdTrafficSnapshot read(List<String> codes, boolean vehicles) {
        Set<String> checkpoints = new HashSet<>();
        Map<String, String> names = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<Instant> timestamps = new ArrayList<>();
        String placeholders = String.join(",", Collections.nCopies(codes.size(), "?"));
        String sql = """
                SELECT h.checkpoint_no, h.region_code, r.name AS region_name,
                       h.route_code, h.route_name, h.average_speed, h.daily_avg_flow, h.temp_2,
                """ + (vehicles ? "h.temp_3" : "NULL AS temp_3") + """
                       , COALESCE(h.update_time, h.create_time) AS data_time
                FROM w_transport_hubs h
                LEFT JOIN w_region_code r ON r.code=h.region_code
                  AND (r.del_flag IS NULL OR r.del_flag IN ('N','0'))
                WHERE (h.del_flag IS NULL OR h.del_flag IN ('N','0'))
                  AND h.region_code IN (
                """ + placeholders + ") ORDER BY h.checkpoint_no";
        var hubs = jdbc.query(sql, (rs, row) -> {
            String checkpoint = required(rs.getString("checkpoint_no"), "卡口编号");
            if (!checkpoints.add(checkpoint)) throw new IllegalArgumentException("卡口编号重复：" + checkpoint);
            String code = rs.getString("region_code");
            var city = FujianCity.fromAdcode(code).orElseThrow();
            String cityName = required(rs.getString("region_name"), "城市名称");
            if (FujianCity.fromName(cityName).orElse(null) != city) {
                throw new IllegalArgumentException("城市代码和名称不一致：" + code);
            }
            long weekly = integer(rs.getString("temp_2"), checkpoint + "的7日流量");
            long daily = integer(rs.getString("daily_avg_flow"), checkpoint + "的日均流量");
            BigDecimal speed = rs.getBigDecimal("average_speed");
            if (speed == null || speed.signum() < 0 || !Double.isFinite(speed.doubleValue())) {
                throw new IllegalArgumentException(checkpoint + "的均速无效");
            }
            String route = rs.getString("route_code");
            String name = rs.getString("route_name");
            Long car = null, bus = null, truck = null;
            if (vehicles) {
                route = required(route, "路线编号").toUpperCase(Locale.ROOT);
                name = name == null || name.isBlank() ? "未提供" : name.trim();
                String normalized = name.replaceAll("[\\p{Pd}\\s]", "");
                String previous = name.equals("未提供") ? null : names.putIfAbsent(route, normalized);
                if (previous != null && !previous.equals(normalized)) {
                    throw new IllegalArgumentException("同一路线名称冲突：" + route);
                }
                JsonNode root;
                try { root = mapper.readTree(rs.getString("temp_3")); }
                catch (Exception e) { throw new IllegalArgumentException(checkpoint + "的车型JSON无法解析"); }
                car = vehicle(root, "car", checkpoint);
                bus = vehicle(root, "bus", checkpoint);
                truck = vehicle(root, "truck", checkpoint);
                if (Math.addExact(Math.addExact(car, bus), truck) != weekly) {
                    warnings.add("卡口" + checkpoint + "的车型合计与7日总流量不一致，已分别保留原值");
                }
            }
            Timestamp timestamp = rs.getTimestamp("data_time");
            if (timestamp != null) timestamps.add(timestamp.toInstant());
            return new OdTransportHub(checkpoint, code, city.displayName() + "市", route, name,
                    weekly, daily, speed.doubleValue(), car, bus, truck);
        }, codes.toArray());
        Instant acquiredAt = timestamps.stream().max(Comparator.naturalOrder()).orElse(clock.instant());
        return new OdTrafficSnapshot(hubs, acquiredAt, warnings);
    }

    private long vehicle(JsonNode root, String key, String checkpoint) {
        JsonNode node = root == null ? null : root.get(key);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong() || node.longValue() < 0) {
            throw new IllegalArgumentException(checkpoint + "的" + key + "车型流量必须为非负整数");
        }
        return node.longValue();
    }

    private long integer(String raw, String label) {
        if (raw == null || !raw.trim().matches("[0-9]+")) {
            throw new IllegalArgumentException(label + "必须为非负整数");
        }
        try { return Long.parseLong(raw.trim()); }
        catch (NumberFormatException e) { throw new IllegalArgumentException(label + "超出数值范围"); }
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + "不能为空");
        return value.trim();
    }
}
