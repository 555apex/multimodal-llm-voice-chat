package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.port.TrafficContextEventPort;
import cn.fj.roadagent.domain.traffic.TrafficContextEvent;
import cn.fj.roadagent.domain.traffic.TrafficContextEventType;
import cn.fj.roadagent.domain.traffic.TrafficContextScope;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 节假日和重大活动是路况补充事实；读取失败时不得阻断核心路况查询。 */
public final class MysqlTrafficContextEventRepository implements TrafficContextEventPort {
    private static final Logger log = LoggerFactory.getLogger(MysqlTrafficContextEventRepository.class);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public MysqlTrafficContextEventRepository(
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
    public List<TrafficContextEvent> findActiveAt(Instant trafficDataTime) {
        if (trafficDataTime == null) return List.of();
        try {
            List<TrafficContextEvent> result = transactionTemplate.execute(status -> load(trafficDataTime));
            return result == null ? List.of() : result;
        } catch (DataAccessException exception) {
            log.warn("节假日及重大活动补充数据暂时不可用，本轮继续返回核心路况：{}", exception.getMessage());
            return List.of();
        } catch (RuntimeException exception) {
            log.warn("节假日及重大活动补充数据读取失败，本轮继续返回核心路况：{}", exception.getMessage());
            return List.of();
        }
    }

    private List<TrafficContextEvent> load(Instant trafficDataTime) {
        LocalDateTime localTime = LocalDateTime.ofInstant(trafficDataTime, BUSINESS_ZONE);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT id, event_code, event_name, event_type,
                       event_start_time, event_end_time, impact_start_time, impact_end_time,
                       scope_type, region_code, region_name, affected_route_codes,
                       venue, data_status
                FROM w_festival_data
                WHERE enabled = 1
                  AND (del_flag IS NULL OR del_flag IN ('N', '0'))
                  AND impact_start_time <= ?
                  AND impact_end_time >= ?
                ORDER BY CASE scope_type WHEN 'ROUTE' THEN 1 WHEN 'CITY' THEN 2 ELSE 3 END,
                         impact_start_time, event_code
                """, Timestamp.valueOf(localTime), Timestamp.valueOf(localTime));
        List<TrafficContextEvent> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            try {
                result.add(map(row));
            } catch (RuntimeException exception) {
                log.warn("忽略非法节假日/活动记录 id={}：{}", row.get("id"), exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private TrafficContextEvent map(Map<String, Object> row) {
        return new TrafficContextEvent(
                ((Number) row.get("id")).longValue(),
                text(row.get("event_code")),
                text(row.get("event_name")),
                TrafficContextEventType.valueOf(text(row.get("event_type")).toUpperCase(Locale.ROOT)),
                instant(row.get("event_start_time")),
                instant(row.get("event_end_time")),
                instant(row.get("impact_start_time")),
                instant(row.get("impact_end_time")),
                TrafficContextScope.valueOf(text(row.get("scope_type")).toUpperCase(Locale.ROOT)),
                nullableText(row.get("region_code")),
                nullableText(row.get("region_name")),
                routeCodes(row.get("affected_route_codes")),
                nullableText(row.get("venue")),
                text(row.get("data_status"))
        );
    }

    private Set<String> routeCodes(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return Set.of();
        try {
            JsonNode node = objectMapper.readTree(String.valueOf(value));
            if (!node.isArray()) throw new IllegalArgumentException("受影响路线必须是JSON数组");
            LinkedHashSet<String> result = new LinkedHashSet<>();
            node.forEach(item -> {
                if (!item.isTextual() || item.asText().isBlank()) {
                    throw new IllegalArgumentException("受影响路线只能包含非空字符串");
                }
                result.add(item.asText());
            });
            return result;
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("受影响路线JSON无法解析", exception);
        }
    }

    private Instant instant(Object value) {
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().atZone(BUSINESS_ZONE).toInstant();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.atZone(BUSINESS_ZONE).toInstant();
        }
        throw new IllegalArgumentException("事件时间字段为空或类型不正确");
    }

    private String text(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("节假日或活动必填字段为空");
        }
        return String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        return value == null || String.valueOf(value).isBlank() ? null : String.valueOf(value).trim();
    }
}
