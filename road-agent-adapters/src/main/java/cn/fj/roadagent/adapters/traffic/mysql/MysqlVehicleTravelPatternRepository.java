package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.VehicleTravelPatternPort;
import cn.fj.roadagent.domain.traffic.VehicleHourlyFlow;
import cn.fj.roadagent.domain.traffic.VehicleTravelPatternSnapshot;
import cn.fj.roadagent.domain.traffic.VehicleType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 按城市create_time读取w_vehicletravelpatternanalyzer最新有效记录。 */
public final class MysqlVehicleTravelPatternRepository implements VehicleTravelPatternPort {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public MysqlVehicleTravelPatternRepository(
            JdbcTemplate jdbcTemplate,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate.setReadOnly(true);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public Optional<VehicleTravelPatternSnapshot> latestForCity(String cityName) {
        try {
            Optional<VehicleTravelPatternSnapshot> result = transactionTemplate.execute(
                    status -> loadLatest(cityName)
            );
            return result == null ? Optional.empty() : result;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ExternalServiceException(
                    "MYSQL_VEHICLE_PATTERN", "VEHICLE_PATTERN_DATA_INVALID",
                    "车型出行特征数据校验失败：" + exception.getMessage(), exception
            );
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(
                    "MYSQL_VEHICLE_PATTERN", "VEHICLE_PATTERN_DATA_UNAVAILABLE",
                    "车型出行特征数据库暂时不可用", exception
            );
        }
    }

    private Optional<VehicleTravelPatternSnapshot> loadLatest(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            throw new IllegalArgumentException("城市不能为空");
        }
        String normalized = cityName.trim().replace("福建省", "").replace("市", "");
        List<VehicleTravelPatternSnapshot> rows = jdbcTemplate.query("""
                SELECT cityName, result1, result2, result3, create_time
                FROM w_vehicletravelpatternanalyzer
                WHERE (del_flag IS NULL OR del_flag IN ('N', '0'))
                  AND cityName IN (?, ?)
                ORDER BY create_time DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> {
            Timestamp createTime = rs.getTimestamp("create_time");
            if (createTime == null) {
                throw new IllegalArgumentException("最新记录create_time为空");
            }
            LocalDateTime localCreateTime = createTime.toLocalDateTime();
            Instant acquiredAt = localCreateTime.atZone(BUSINESS_ZONE).toInstant();
            return new VehicleTravelPatternSnapshot(
                    rs.getString("cityName"),
                    parseTotals(rs.getString("result1"), "result1"),
                    parseHourly(rs.getString("result2")),
                    parseTotals(rs.getString("result3"), "result3"),
                    localCreateTime.toLocalDate(),
                    acquiredAt
            );
        }, normalized, normalized + "市");
        return rows.stream().findFirst();
    }

    private Map<VehicleType, Long> parseTotals(String json, String label) {
        JsonNode root = parseObject(json, label);
        EnumMap<VehicleType, Long> result = new EnumMap<>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            JsonNode value = root.get(type.databaseKey());
            if (value == null) {
                throw new IllegalArgumentException(label + "缺少" + type.databaseKey());
            }
            result.put(type, nonNegativeLong(value, label + "." + type.databaseKey()));
        }
        return result;
    }

    private List<VehicleHourlyFlow> parseHourly(String json) {
        JsonNode root = parseObject(json, "result2");
        List<VehicleHourlyFlow> rows = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            LocalDateTime hour;
            try {
                hour = LocalDateTime.parse(field.getKey(), HOUR_FORMAT);
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException("result2时间格式不正确：" + field.getKey(), exception);
            }
            if (!field.getValue().isObject()) {
                throw new IllegalArgumentException("result2小时值必须是车型对象");
            }
            EnumMap<VehicleType, Long> volumes = new EnumMap<>(VehicleType.class);
            for (VehicleType type : VehicleType.values()) {
                JsonNode node = field.getValue().get(type.databaseKey());
                volumes.put(type, node == null ? 0L
                        : nonNegativeLong(node, "result2." + field.getKey() + "." + type.databaseKey()));
            }
            rows.add(new VehicleHourlyFlow(hour, volumes));
        }
        return rows.stream().sorted(java.util.Comparator.comparing(VehicleHourlyFlow::hour)).toList();
    }

    private JsonNode parseObject(String json, String label) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException(label + "为空");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root == null || !root.isObject()) {
                throw new IllegalArgumentException(label + "必须是JSON对象");
            }
            return root;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(label + "不是有效JSON", exception);
        }
    }

    private long nonNegativeLong(JsonNode node, String label) {
        if (!node.isIntegralNumber() || !node.canConvertToLong()) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
        long value = node.longValue();
        if (value < 0) {
            throw new IllegalArgumentException(label + "不能为负数");
        }
        return value;
    }
}
