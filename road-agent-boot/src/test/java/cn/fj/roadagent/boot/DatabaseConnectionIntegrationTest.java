package cn.fj.roadagent.boot;

import cn.fj.roadagent.adapters.event.mysql.AbnormalEventRepository;
import cn.fj.roadagent.application.port.DispatchRepository;
import cn.fj.roadagent.application.port.ResourceDataPort;
import cn.fj.roadagent.domain.dispatch.DispatchPlan;
import cn.fj.roadagent.domain.dispatch.DispatchStatus;
import cn.fj.roadagent.domain.dispatch.SuggestedResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "ROADAGENT_DB_URL", matches = ".+")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "roadagent.traffic.snapshot-poll-seconds=60",
                "roadagent.traffic.snapshot-stable-seconds=30",
                "roadagent.model.provider=openai-compatible",
                "roadagent.model.api-key=test-model-key"
        }
)
class DatabaseConnectionIntegrationTest {

    @Autowired
    private AbnormalEventRepository abnormalEventRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DispatchRepository dispatchRepository;

    @Autowired
    private ResourceDataPort resourceDataPort;

    @Test
    void shouldConnectAndFindInsertedTestEvents() {
        assertTrue(abnormalEventRepository.countEvents() > 0);

        Integer completeEventCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM w_abnormal_event
                WHERE id IS NOT NULL
                  AND event_type IS NOT NULL
                  AND description IS NOT NULL
                """,
                Integer.class
        );
        assertTrue(completeEventCount != null && completeEventCount > 0);
    }

    @Test
    void shouldReturnOldestPendingEventWithBigIdAsString() {
        var event = abnormalEventRepository.findNextPending().orElseThrow();
        String expectedId = jdbcTemplate.queryForObject(
                """
                SELECT CAST(e.id AS CHAR)
                FROM w_abnormal_event e
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.id
                WHERE e.event_status = 0
                  AND (e.del_flag IS NULL OR e.del_flag IN ('N', '0'))
                  AND (w.workflow_id IS NULL OR (
                      w.current_stage = 1 AND w.workflow_status IN (0, 1, 2, 5, 6)
                  ))
                ORDER BY e.occurrence_time IS NULL, e.occurrence_time ASC,
                         w.stage_entered_at ASC, e.id ASC
                LIMIT 1
                """,
                String.class
        );

        assertEquals(expectedId, event.eventId());
        assertTrue(event.eventId().length() > 15);
        Long expectedCount = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM w_abnormal_event e
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.id
                WHERE e.event_status = 0
                  AND (e.del_flag IS NULL OR e.del_flag IN ('N', '0'))
                  AND (w.workflow_id IS NULL OR (
                      w.current_stage = 1 AND w.workflow_status IN (0, 1, 2, 5, 6)
                  ))
                """,
                Long.class
        );
        assertEquals(expectedCount, abnormalEventRepository.countPending());
    }

    @Test
    void shouldRecognizeCurrentAndLegacyActiveDeletionFlags() {
        List<String> activeFlags = jdbcTemplate.queryForList(
                """
                SELECT COALESCE(flag_value, 'NULL')
                FROM (
                    SELECT 'N' AS flag_value
                    UNION ALL SELECT '0'
                    UNION ALL SELECT NULL
                    UNION ALL SELECT 'Y'
                    UNION ALL SELECT '1'
                ) flags
                WHERE flag_value IS NULL OR flag_value IN ('N', '0')
                ORDER BY COALESCE(flag_value, 'NULL')
                """,
                String.class
        );

        assertEquals(List.of("0", "N", "NULL"), activeFlags);
    }

    @Test
    @Transactional
    void shouldPersistAndRestoreDispatchVersionFromMysql() {
        var candidate = abnormalEventRepository.findNextPending().orElseThrow();
        var event = abnormalEventRepository.lockPendingById(candidate.eventId()).orElseThrow();
        Instant now = Instant.now();
        var existing = dispatchRepository.findLatestByEventId(event.eventId());
        String planId = existing
                .map(DispatchPlan::planId)
                .orElseGet(() -> "IT-" + UUID.randomUUID());
        long version = existing.map(plan -> plan.version() + 1).orElse(1L);
        DispatchPlan generating = DispatchPlan.generating(planId, event, version, now);

        assertTrue(dispatchRepository.insert(generating));
        DispatchPlan waiting = generating.generated(
                List.of(new SuggestedResource(
                        "抢险队伍", "集成测试建议队伍", 1, "组", "验证JSON持久化"
                )),
                "仅用于事务内集成测试，不会提交到正式工单。",
                now.plusSeconds(1)
        );
        assertTrue(dispatchRepository.updateGenerated(waiting));

        DispatchPlan restored = dispatchRepository.findLatestByPlanId(planId).orElseThrow();
        assertEquals(event.eventId(), restored.event().eventId());
        assertEquals(DispatchStatus.WAITING_APPROVAL, restored.status());
        assertEquals("集成测试建议队伍", restored.suggestedResources().get(0).resourceName());
    }

    @Test
    @Transactional
    void shouldLoadNineCityResourcesAndRejectStaleInventoryVersion() {
        var resources = resourceDataPort.listActiveForPlanning("DT01");
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM w_emergency_resource", Integer.class
        ) >= 108);
        assertEquals(9, resources.stream().map(item -> item.cityCode()).distinct().count());
        var candidate = resources.stream().filter(item -> item.availableQuantity() > 0)
                .findFirst().orElseThrow();
        var locked = resourceDataPort.lockByResourceIds(Set.of(candidate.resourceId()))
                .get(0);
        var reserved = locked.reserve(1);
        assertTrue(resourceDataPort.updateInventory(reserved, locked.lockVersion()));
        assertTrue(!resourceDataPort.updateInventory(reserved, locked.lockVersion()));
    }
}
