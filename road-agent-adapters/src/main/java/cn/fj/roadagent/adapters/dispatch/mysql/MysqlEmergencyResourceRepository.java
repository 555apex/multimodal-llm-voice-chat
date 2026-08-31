package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.application.dispatch.ResourceQuery;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.domain.dispatch.EmergencyResource;
import cn.fj.roadagent.domain.dispatch.EmergencyResourceStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Repository
public class MysqlEmergencyResourceRepository implements ResourceDataPort {
    private static final String COLUMNS = """
            SELECT resource_id, resource_type_code, resource_type_name, resource_name,
                   city_code, city_name, unit, capability, applicable_event_types,
                   total_quantity, available_quantity, reserved_quantity,
                   dispatched_quantity, minimum_reserve_quantity,
                   resource_status, lock_version
            FROM w_emergency_resource
            """;
    private static final String ACTIVE = """
            resource_status = 0 AND (del_flag IS NULL OR del_flag IN ('N', '0'))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<EmergencyResource> mapper = this::mapResource;

    public MysqlEmergencyResourceRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<EmergencyResource> search(ResourceQuery query) {
        String city = query == null ? null : query.city();
        List<String> types = query == null ? List.of() : query.resourceTypes();
        return listActiveForPlanning(null).stream()
                .filter(item -> city == null || city.isBlank()
                        || item.city().contains(city) || city.contains(item.city()))
                .filter(item -> types == null || types.isEmpty() || types.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(value -> value.toLowerCase(Locale.ROOT))
                        .anyMatch(value -> (item.typeCode() + item.type() + item.name()
                                + item.capability()).toLowerCase(Locale.ROOT).contains(value)))
                .toList();
    }

    @Override
    public List<EmergencyResource> listActiveForPlanning(String eventType) {
        return jdbcTemplate.query(
                COLUMNS + " WHERE " + ACTIVE + " ORDER BY city_code, resource_type_code, resource_id",
                mapper
        ).stream().filter(item -> eventType == null || item.appliesTo(eventType)).toList();
    }

    @Override
    public List<EmergencyResource> lockByTypeCodes(Set<String> typeCodes) {
        return lockBy("resource_type_code", typeCodes, true);
    }

    @Override
    public List<EmergencyResource> lockByResourceIds(Set<String> resourceIds) {
        return lockBy("resource_id", resourceIds, false);
    }

    @Override
    public boolean updateInventory(EmergencyResource resource, long expectedLockVersion) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_resource
                SET available_quantity = ?, reserved_quantity = ?, dispatched_quantity = ?,
                    lock_version = ?, update_time = CURRENT_TIMESTAMP(6)
                WHERE resource_id = ? AND lock_version = ?
                """,
                resource.availableQuantity(), resource.reservedQuantity(),
                resource.dispatchedQuantity(), resource.lockVersion(),
                resource.resourceId(), expectedLockVersion
        ) == 1;
    }

    private List<EmergencyResource> lockBy(String column, Set<String> values, boolean activeOnly) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> ordered = values.stream().sorted().toList();
        String placeholders = String.join(",", java.util.Collections.nCopies(ordered.size(), "?"));
        List<Object> parameters = new ArrayList<>(ordered);
        return jdbcTemplate.query(
                COLUMNS + " WHERE " + (activeOnly ? ACTIVE + " AND " : "")
                        + column + " IN (" + placeholders + ")"
                        + " ORDER BY resource_id FOR UPDATE",
                mapper,
                parameters.toArray()
        );
    }

    private EmergencyResource mapResource(java.sql.ResultSet rows, int rowNumber)
            throws java.sql.SQLException {
        return new EmergencyResource(
                rows.getString("resource_id"), rows.getString("resource_type_code"),
                rows.getString("resource_type_name"), rows.getString("resource_name"),
                rows.getString("city_code"), rows.getString("city_name"), rows.getString("unit"),
                rows.getString("capability"), readEventTypes(rows.getString("applicable_event_types")),
                rows.getInt("total_quantity"), rows.getInt("available_quantity"),
                rows.getInt("reserved_quantity"), rows.getInt("dispatched_quantity"),
                rows.getInt("minimum_reserve_quantity"),
                fromStatus(rows.getInt("resource_status")), rows.getLong("lock_version")
        );
    }

    private List<String> readEventTypes(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("资源适用事件类型不是有效JSON", exception);
        }
    }

    private EmergencyResourceStatus fromStatus(int value) {
        return switch (value) {
            case 0 -> EmergencyResourceStatus.ACTIVE;
            case 1 -> EmergencyResourceStatus.MAINTENANCE;
            case 2 -> EmergencyResourceStatus.DISABLED;
            default -> throw new IllegalStateException("数据库资源状态无效：" + value);
        };
    }
}
