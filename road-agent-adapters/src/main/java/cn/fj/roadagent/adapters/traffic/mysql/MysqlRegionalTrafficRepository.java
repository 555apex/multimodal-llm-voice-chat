package cn.fj.roadagent.adapters.traffic.mysql;

import cn.fj.roadagent.application.exception.ExternalServiceException;
import cn.fj.roadagent.application.port.RegionalTrafficDataPort;
import cn.fj.roadagent.domain.traffic.FujianCity;
import cn.fj.roadagent.domain.traffic.RegionalTrafficSnapshot;
import cn.fj.roadagent.domain.traffic.TransportHub;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 即时读取w_transport_hubs和w_region_code，不参与路况快照稳定等待。 */
public final class MysqlRegionalTrafficRepository implements RegionalTrafficDataPort {
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public MysqlRegionalTrafficRepository(
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
    public RegionalTrafficSnapshot load() {
        try {
            RegionalTrafficSnapshot snapshot = transactionTemplate.execute(status -> loadInTransaction());
            if (snapshot == null) {
                throw new IllegalStateException("区域交通查询事务未返回结果");
            }
            return snapshot;
        } catch (ExternalServiceException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new ExternalServiceException(
                    "MYSQL_REGIONAL_TRAFFIC", "REGIONAL_TRAFFIC_DATA_INVALID",
                    "区域交通数据校验失败：" + exception.getMessage(), exception
            );
        } catch (RuntimeException exception) {
            throw new ExternalServiceException(
                    "MYSQL_REGIONAL_TRAFFIC", "REGIONAL_TRAFFIC_DATA_UNAVAILABLE",
                    "区域交通数据库暂时不可用", exception
            );
        }
    }

    private RegionalTrafficSnapshot loadInTransaction() {
        List<TransportHub> hubs = jdbcTemplate.query("""
                SELECT h.checkpoint_no, h.route_code, h.route_name, h.average_speed,
                       h.region_code, r.name AS region_name, h.daily_avg_flow
                FROM w_transport_hubs h
                LEFT JOIN w_region_code r
                  ON r.code = h.region_code
                 AND (r.del_flag IS NULL OR r.del_flag IN ('N', '0'))
                WHERE h.del_flag IS NULL OR h.del_flag IN ('N', '0')
                ORDER BY h.checkpoint_no
                """, (rs, rowNum) -> new TransportHub(
                rs.getString("checkpoint_no"),
                rs.getString("route_code"),
                rs.getString("route_name"),
                requiredDouble(rs.getObject("average_speed"), "卡口均速"),
                rs.getString("region_code"),
                rs.getString("region_name"),
                requiredLong(rs.getObject("daily_avg_flow"), "卡口日均流量")
        ));
        validate(hubs);
        Timestamp timestamp = jdbcTemplate.queryForObject("""
                SELECT MAX(COALESCE(update_time, create_time))
                FROM w_transport_hubs
                WHERE del_flag IS NULL OR del_flag IN ('N', '0')
                """, Timestamp.class);
        Instant acquiredAt = timestamp == null ? clock.instant() : timestamp.toInstant();
        return new RegionalTrafficSnapshot(hubs, acquiredAt);
    }

    private void validate(List<TransportHub> hubs) {
        if (hubs.isEmpty()) {
            throw new IllegalArgumentException("没有可用卡口记录");
        }
        Set<String> checkpoints = new HashSet<>();
        Map<String, String> routeNames = new HashMap<>();
        for (TransportHub hub : hubs) {
            if (!checkpoints.add(hub.checkpointNo())) {
                throw new IllegalArgumentException("卡口编号重复：" + hub.checkpointNo());
            }
            FujianCity city = FujianCity.fromAdcode(hub.regionCode()).orElseThrow(() ->
                    new IllegalArgumentException("区域代码无法映射福建地级市：" + hub.regionCode()));
            if (!normalizeCity(hub.regionName()).equals(city.displayName())) {
                throw new IllegalArgumentException("区域代码与名称不一致：" + hub.regionCode());
            }
            String oldName = routeNames.putIfAbsent(hub.routeCode(), normalizeRouteName(hub.routeName()));
            if (oldName != null && !oldName.equals(normalizeRouteName(hub.routeName()))) {
                throw new IllegalArgumentException("同一路线编号存在多个路线名称：" + hub.routeCode());
            }
        }
    }

    private double requiredDouble(Object value, String label) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(label + "存在空值或非数值");
        }
        return number.doubleValue();
    }

    private long requiredLong(Object value, String label) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(label + "存在空值或非数值");
        }
        return number.longValue();
    }

    private String normalizeCity(String value) {
        return value == null ? "" : value.trim().replace("福建省", "").replace("市", "");
    }

    private String normalizeRouteName(String value) {
        return value == null ? "" : value.replaceAll("[\\p{Pd}\\s]", "").toLowerCase(Locale.ROOT);
    }
}
