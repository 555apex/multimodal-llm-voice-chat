package cn.fj.roadagent.adapters.dispatch.mysql;

import cn.fj.roadagent.domain.dispatch.AllocatedResource;
import cn.fj.roadagent.domain.dispatch.DispatchScope;
import cn.fj.roadagent.domain.dispatch.ResourceAllocation;
import cn.fj.roadagent.domain.dispatch.ResourceAllocationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MysqlEmergencyResourceRepositoryTest {
    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");
    private JdbcTemplate jdbc;
    private MysqlEmergencyResourceRepository resources;
    private MysqlResourceAllocationRepository allocations;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:emergency-resource;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", ""
        );
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP ALL OBJECTS");
        jdbc.execute("""
                CREATE TABLE w_emergency_resource (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    resource_id VARCHAR(40) NOT NULL UNIQUE,
                    resource_type_code VARCHAR(40) NOT NULL,
                    resource_type_name VARCHAR(50) NOT NULL,
                    resource_name VARCHAR(100) NOT NULL,
                    city_code VARCHAR(6) NOT NULL,
                    city_name VARCHAR(20) NOT NULL,
                    longitude DECIMAL(10,6),
                    latitude DECIMAL(9,6),
                    unit VARCHAR(20) NOT NULL,
                    capability VARCHAR(500) NOT NULL,
                    applicable_event_types CLOB NOT NULL,
                    total_quantity INT NOT NULL,
                    available_quantity INT NOT NULL,
                    reserved_quantity INT NOT NULL,
                    dispatched_quantity INT NOT NULL,
                    minimum_reserve_quantity INT NOT NULL,
                    resource_status TINYINT NOT NULL,
                    lock_version BIGINT NOT NULL,
                    del_flag VARCHAR(1),
                    update_time TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE w_emergency_resource_allocation (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    allocation_id VARCHAR(40) NOT NULL UNIQUE,
                    workflow_id VARCHAR(40) NOT NULL,
                    event_id BIGINT NOT NULL,
                    plan_id VARCHAR(40) NOT NULL,
                    plan_version INT NOT NULL,
                    resource_id VARCHAR(40) NOT NULL,
                    resource_type_code VARCHAR(40) NOT NULL,
                    resource_type_name VARCHAR(50) NOT NULL,
                    resource_name VARCHAR(100) NOT NULL,
                    source_city_code VARCHAR(6) NOT NULL,
                    source_city_name VARCHAR(20) NOT NULL,
                    allocated_quantity INT NOT NULL,
                    unit VARCHAR(20) NOT NULL,
                    purpose VARCHAR(300) NOT NULL,
                    estimated_distance_km DECIMAL(8,1) NOT NULL,
                    dispatch_scope VARCHAR(20) NOT NULL,
                    allocation_status TINYINT NOT NULL,
                    reserved_time TIMESTAMP NOT NULL,
                    dispatched_time TIMESTAMP,
                    released_time TIMESTAMP,
                    release_reason VARCHAR(500),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    UNIQUE(plan_id, plan_version, resource_id)
                )
                """);
        insertResource("ER-ACTIVE", 0, "N", "[\"DT01\"]");
        insertResource("ER-MAINTENANCE", 1, "N", "[\"DT01\"]");
        insertResource("ER-DELETED", 0, "Y", "[\"DT01\"]");
        resources = new MysqlEmergencyResourceRepository(
                jdbc, new ObjectMapper().findAndRegisterModules()
        );
        allocations = new MysqlResourceAllocationRepository(jdbc);
    }

    @Test
    void shouldFilterInactiveResourcesAndUseOptimisticInventoryUpdates() {
        assertEquals(1, resources.listActiveForPlanning("DT01").size());
        assertEquals(119.2965, resources.cityCenters().get("350100").longitude(), 0.000001);
        var locked = resources.lockByTypeCodes(Set.of("ROAD_RESCUE_TEAM"));
        assertEquals(1, locked.size());
        var original = locked.get(0);
        var reserved = original.reserve(2);
        assertTrue(resources.updateInventory(reserved, original.lockVersion()));
        assertFalse(resources.updateInventory(reserved, original.lockVersion()));
        assertEquals(3, resources.lockByResourceIds(Set.of("ER-ACTIVE"))
                .get(0).availableQuantity());
    }

    @Test
    void shouldPersistImmutableAllocationSnapshotAndConditionalLifecycle() {
        AllocatedResource snapshot = new AllocatedResource(
                "ER-ACTIVE", "ROAD_RESCUE_TEAM", "公路抢险队伍", "福州抢险资源池",
                "350100", "福州", 2, "组", "道路抢通", 0, DispatchScope.LOCAL
        );
        ResourceAllocation reserved = ResourceAllocation.reserved(
                "RA-1", "WF-1", "9007199254740993", "DP-1", 1, snapshot, NOW
        );
        assertTrue(allocations.insert(reserved));
        assertFalse(allocations.insert(reserved));
        ResourceAllocation restored = allocations.findByPlanVersion("DP-1", 1).get(0);
        assertEquals(snapshot, restored.resource());
        ResourceAllocation dispatched = restored.dispatched(NOW.plusSeconds(1));
        assertTrue(allocations.update(dispatched, restored));
        assertFalse(allocations.update(dispatched, restored));
        assertEquals(ResourceAllocationStatus.DISPATCHED,
                allocations.findByPlanVersion("DP-1", 1).get(0).status());
    }

    private void insertResource(String resourceId, int status, String delFlag, String eventTypes) {
        jdbc.update("""
                INSERT INTO w_emergency_resource (
                    resource_id, resource_type_code, resource_type_name, resource_name,
                    city_code, city_name, longitude, latitude, unit, capability, applicable_event_types,
                    total_quantity, available_quantity, reserved_quantity,
                    dispatched_quantity, minimum_reserve_quantity, resource_status,
                    lock_version, del_flag, update_time
                ) VALUES (?, 'ROAD_RESCUE_TEAM', '公路抢险队伍', '福州抢险资源池',
                          '350100', '福州', 119.296500, 26.074500, '组', '道路抢通', ?,
                          5, 5, 0, 0, 1, ?, 0, ?, CURRENT_TIMESTAMP)
                """, resourceId, eventTypes, status, delFlag);
    }
}
