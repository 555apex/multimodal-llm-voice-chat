package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.application.port.EmergencyResponsePlanPort;
import cn.fj.roadagent.domain.dispatch.EmergencyResponsePlan;
import cn.fj.roadagent.domain.dispatch.ResponsePlanResourceBaseline;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** MySQL版本化应急预案只读适配器。 */
@Repository
public class MysqlEmergencyResponsePlanRepository implements EmergencyResponsePlanPort {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public MysqlEmergencyResponsePlanRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<EmergencyResponsePlan> findActiveByEventType(String eventType) {
        return jdbcTemplate.query("""
                SELECT plan_id, event_type, event_type_name, plan_version,
                       required_facts, rescue_plan_template, resource_baseline, content_hash
                FROM w_emergency_response_plan
                WHERE event_type = ? AND plan_status = 1
                ORDER BY plan_version DESC
                LIMIT 2
                """, (rows, rowNumber) -> new EmergencyResponsePlan(
                rows.getString("plan_id"), rows.getString("event_type"),
                rows.getString("event_type_name"), rows.getLong("plan_version"),
                read(rows.getString("required_facts"), new TypeReference<List<String>>() { },
                        "必需现场事实"),
                rows.getString("rescue_plan_template"),
                read(rows.getString("resource_baseline"),
                        new TypeReference<List<ResponsePlanResourceBaseline>>() { }, "资源基线"),
                rows.getString("content_hash")
        ), eventType).stream().reduce((first, second) -> {
            throw new IllegalStateException("同一事件类型存在多个已发布预案：" + eventType);
        });
    }

    private <T> T read(String json, TypeReference<T> type, String label) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("数据库预案的" + label + "不是有效JSON", exception);
        }
    }
}
