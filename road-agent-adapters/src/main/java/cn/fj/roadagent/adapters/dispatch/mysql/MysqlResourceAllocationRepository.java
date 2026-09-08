package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.application.port.ResourceAllocationPort;
import cn.fj.roadagent.domain.dispatch.AllocatedResource;
import cn.fj.roadagent.domain.dispatch.DispatchScope;
import cn.fj.roadagent.domain.dispatch.ResourceAllocation;
import cn.fj.roadagent.domain.dispatch.ResourceAllocationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

@Repository
public class MysqlResourceAllocationRepository implements ResourceAllocationPort {
    private static final String COLUMNS = """
            SELECT allocation_id, workflow_id, event_id, plan_id, plan_version,
                   resource_id, resource_type_code, resource_type_name, resource_name,
                   source_city_code, source_city_name, allocated_quantity, unit, purpose,
                   estimated_distance_km, dispatch_scope, allocation_status,
                   reserved_time, dispatched_time, released_time, release_reason
            FROM w_emergency_resource_allocation
            """;
    private final JdbcTemplate jdbcTemplate;

    public MysqlResourceAllocationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean insert(ResourceAllocation allocation) {
        AllocatedResource resource = allocation.resource();
        try {
            return jdbcTemplate.update(
                    """
                    INSERT INTO w_emergency_resource_allocation (
                        allocation_id, workflow_id, event_id, plan_id, plan_version,
                        resource_id, resource_type_code, resource_type_name, resource_name,
                        source_city_code, source_city_name, allocated_quantity, unit, purpose,
                        estimated_distance_km, dispatch_scope, allocation_status,
                        reserved_time, dispatched_time, released_time, release_reason,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    allocation.allocationId(), allocation.workflowId(), allocation.eventId(),
                    allocation.planId(), allocation.planVersion(), resource.resourceId(),
                    resource.resourceTypeCode(), resource.resourceTypeName(), resource.resourceName(),
                    resource.sourceCityCode(), resource.sourceCityName(), resource.quantity(),
                    resource.unit(), resource.purpose(), resource.estimatedDistanceKm(),
                    resource.dispatchScope().name(), statusValue(allocation.status()),
                    timestamp(allocation.reservedAt()), timestamp(allocation.dispatchedAt()),
                    timestamp(allocation.releasedAt()), allocation.releaseReason(),
                    timestamp(allocation.reservedAt()), timestamp(allocation.reservedAt())
            ) == 1;
        } catch (DuplicateKeyException exception) {
            return false;
        }
    }

    @Override
    public List<ResourceAllocation> findByPlanVersion(String planId, long planVersion) {
        return jdbcTemplate.query(
                COLUMNS + " WHERE plan_id = ? AND plan_version = ? ORDER BY id",
                (rows, rowNumber) -> new ResourceAllocation(
                        rows.getString("allocation_id"), rows.getString("workflow_id"),
                        rows.getString("event_id"), rows.getString("plan_id"),
                        rows.getLong("plan_version"),
                        new AllocatedResource(
                                rows.getString("resource_id"), rows.getString("resource_type_code"),
                                rows.getString("resource_type_name"), rows.getString("resource_name"),
                                rows.getString("source_city_code"), rows.getString("source_city_name"),
                                rows.getInt("allocated_quantity"), rows.getString("unit"),
                                rows.getString("purpose"), rows.getDouble("estimated_distance_km"),
                                DispatchScope.valueOf(rows.getString("dispatch_scope"))
                        ),
                        fromStatus(rows.getInt("allocation_status")),
                        instant(rows.getTimestamp("reserved_time")),
                        instant(rows.getTimestamp("dispatched_time")),
                        instant(rows.getTimestamp("released_time")),
                        rows.getString("release_reason")
                ),
                planId, planVersion
        );
    }

    @Override
    public boolean update(ResourceAllocation allocation, ResourceAllocation expected) {
        return jdbcTemplate.update(
                """
                UPDATE w_emergency_resource_allocation
                SET allocation_status = ?, dispatched_time = ?, released_time = ?,
                    release_reason = ?, update_time = CURRENT_TIMESTAMP(6)
                WHERE allocation_id = ? AND allocation_status = ?
                """,
                statusValue(allocation.status()), timestamp(allocation.dispatchedAt()),
                timestamp(allocation.releasedAt()), allocation.releaseReason(),
                allocation.allocationId(), statusValue(expected.status())
        ) == 1;
    }

    private int statusValue(ResourceAllocationStatus status) {
        return switch (status) {
            case RESERVED -> 0;
            case DISPATCHED -> 1;
            case RELEASED -> 2;
        };
    }

    private ResourceAllocationStatus fromStatus(int value) {
        return switch (value) {
            case 0 -> ResourceAllocationStatus.RESERVED;
            case 1 -> ResourceAllocationStatus.DISPATCHED;
            case 2 -> ResourceAllocationStatus.RELEASED;
            default -> throw new IllegalStateException("资源占用状态无效：" + value);
        };
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
