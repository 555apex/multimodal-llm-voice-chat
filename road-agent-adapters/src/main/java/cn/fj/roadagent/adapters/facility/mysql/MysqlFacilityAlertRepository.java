package cn.fj.roadagent.adapters.facility.mysql;

import cn.fj.roadagent.application.facility.FacilityAlertCounts;
import cn.fj.roadagent.application.port.FacilityAlertPort;
import cn.fj.roadagent.domain.facility.AlarmLevel;
import cn.fj.roadagent.domain.facility.FacilityAlert;
import cn.fj.roadagent.domain.facility.FacilityAlertStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** w_realtime_abnormal 的唯一数据库适配器。 */
@Repository
public class MysqlFacilityAlertRepository implements FacilityAlertPort {
    private static final String COLUMNS = """
            id, facility_name, metric_name, actual_value, actual_str_value,
            threshold_min, threshold_max, alarm_level, collect_time, trigger_time,
            status, remark
            """;

    private final JdbcTemplate jdbcTemplate;

    public MysqlFacilityAlertRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<FacilityAlert> findPage(
            FacilityAlertStatus status, AlarmLevel alarmLevel, int offset, int limit
    ) {
        QueryParts query = filters(status, alarmLevel);
        query.arguments.add(limit);
        query.arguments.add(offset);
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM w_realtime_abnormal " + query.where
                        + " ORDER BY alarm_level DESC, trigger_time ASC, id ASC LIMIT ? OFFSET ?",
                this::mapAlert,
                query.arguments.toArray()
        );
    }

    @Override
    public long count(FacilityAlertStatus status, AlarmLevel alarmLevel) {
        QueryParts query = filters(status, alarmLevel);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM w_realtime_abnormal " + query.where,
                Long.class,
                query.arguments.toArray()
        );
        return count == null ? 0 : count;
    }

    @Override
    public FacilityAlertCounts counts() {
        return jdbcTemplate.queryForObject("""
                SELECT
                  COUNT(CASE WHEN status=1 THEN 1 END) AS pending_count,
                  COUNT(CASE WHEN status=2 THEN 1 END) AS confirmed_count,
                  COUNT(CASE WHEN status=3 THEN 1 END) AS closed_count,
                  COUNT(CASE WHEN status IN (1,2) AND alarm_level=1 THEN 1 END) AS warning_count,
                  COUNT(CASE WHEN status IN (1,2) AND alarm_level=2 THEN 1 END) AS severe_count,
                  COUNT(CASE WHEN status IN (1,2) AND alarm_level=3 THEN 1 END) AS emergency_count,
                  MAX(collect_time) AS data_as_of
                FROM w_realtime_abnormal
                WHERE COALESCE(del_flag, 'N') = 'N'
                """, (rs, rowNum) -> new FacilityAlertCounts(
                rs.getLong("pending_count"),
                rs.getLong("confirmed_count"),
                rs.getLong("closed_count"),
                rs.getLong("warning_count"),
                rs.getLong("severe_count"),
                rs.getLong("emergency_count"),
                toInstant(rs.getTimestamp("data_as_of"))
        ));
    }

    @Override
    public List<FacilityAlert> findActive() {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM w_realtime_abnormal "
                        + "WHERE COALESCE(del_flag, 'N') = 'N' AND status IN (1,2) "
                        + "ORDER BY alarm_level DESC, trigger_time ASC, id ASC",
                this::mapAlert
        );
    }

    @Override
    public Optional<FacilityAlert> findById(long alertId) {
        return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM w_realtime_abnormal "
                        + "WHERE id=? AND COALESCE(del_flag, 'N') = 'N' LIMIT 1",
                this::mapAlert,
                alertId
        ).stream().findFirst();
    }

    @Override
    public boolean updateStatus(
            long alertId,
            FacilityAlertStatus expectedStatus,
            FacilityAlertStatus targetStatus,
            String remark
    ) {
        return jdbcTemplate.update("""
                UPDATE w_realtime_abnormal
                SET status=?, remark=?
                WHERE id=? AND status=? AND COALESCE(del_flag, 'N') = 'N'
                """,
                targetStatus.databaseValue(), remark, alertId, expectedStatus.databaseValue()
        ) == 1;
    }

    private QueryParts filters(FacilityAlertStatus status, AlarmLevel alarmLevel) {
        List<String> conditions = new ArrayList<>();
        conditions.add("COALESCE(del_flag, 'N') = 'N'");
        List<Object> arguments = new ArrayList<>();
        if (status != null) {
            conditions.add("status=?");
            arguments.add(status.databaseValue());
        }
        if (alarmLevel != null) {
            conditions.add("alarm_level=?");
            arguments.add(alarmLevel.databaseValue());
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new QueryParts(where, arguments);
    }

    private FacilityAlert mapAlert(ResultSet rs, int rowNum) throws SQLException {
        return new FacilityAlert(
                rs.getLong("id"),
                rs.getString("facility_name"),
                rs.getString("metric_name"),
                rs.getBigDecimal("actual_value"),
                rs.getString("actual_str_value"),
                rs.getBigDecimal("threshold_min"),
                rs.getBigDecimal("threshold_max"),
                AlarmLevel.fromDatabase(rs.getInt("alarm_level")),
                toInstant(rs.getTimestamp("collect_time")),
                toInstant(rs.getTimestamp("trigger_time")),
                FacilityAlertStatus.fromDatabase(rs.getInt("status")),
                rs.getString("remark")
        );
    }

    private static Instant toInstant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private record QueryParts(String where, List<Object> arguments) {
    }
}
