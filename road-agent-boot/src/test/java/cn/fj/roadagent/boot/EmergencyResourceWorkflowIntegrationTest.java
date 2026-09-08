package cn.fj.roadagent.boot;

import cn.fj.roadagent.application.dispatch.CommandDecisionCommand;
import cn.fj.roadagent.application.dispatch.Level1Decision;
import cn.fj.roadagent.application.dispatch.Level1DecisionCommand;
import cn.fj.roadagent.application.dispatch.ProfessionalReviewCommand;
import cn.fj.roadagent.application.dispatch.ReleaseResourcesCommand;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.core.dispatch.DispatchApplicationService;
import cn.fj.roadagent.core.dispatch.DispatchPlanProposal;
import cn.fj.roadagent.domain.dispatch.ApprovalDecision;
import cn.fj.roadagent.domain.dispatch.DispatchScope;
import cn.fj.roadagent.domain.dispatch.EventSeverity;
import cn.fj.roadagent.domain.dispatch.ResourceFeasibility;
import cn.fj.roadagent.domain.dispatch.WorkflowStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.AfterTransaction;
import org.springframework.test.context.transaction.BeforeTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** 使用共享MySQL真实表验证资源工作流；每个测试结束后整笔回滚。 */
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
class EmergencyResourceWorkflowIntegrationTest {
    @Autowired
    private DispatchApplicationService service;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private ChatModelPort chatModel;

    private DatabaseSnapshot baseline;

    @BeforeTransaction
    void captureDatabaseBaseline() {
        baseline = snapshot();
    }

    @AfterTransaction
    void shouldRollbackEveryWorkflowAndInventoryChange() {
        assertEquals(baseline, snapshot());
    }

    @Test
    @Transactional
    void shouldAllocateAcrossCitiesPublishAndReleaseAgainstRealMysql() {
        var candidate = jdbc.queryForMap("""
                SELECT e.c_no AS event_id,
                       remote_resource.resource_type_code
                FROM (
                    SELECT incident.*,
                           CASE
                             WHEN source_name LIKE '%福州%' THEN '350100'
                             WHEN source_name LIKE '%厦门%' THEN '350200'
                             WHEN source_name LIKE '%莆田%' THEN '350300'
                             WHEN source_name LIKE '%三明%' THEN '350400'
                             WHEN source_name LIKE '%泉州%' THEN '350500'
                             WHEN source_name LIKE '%漳州%' THEN '350600'
                             WHEN source_name LIKE '%南平%' THEN '350700'
                             WHEN source_name LIKE '%龙岩%' THEN '350800'
                             WHEN source_name LIKE '%宁德%' THEN '350900'
                           END AS event_city_code
                    FROM w_lw_incident incident
                ) e
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.c_no
                JOIN w_emergency_resource remote_resource
                  ON JSON_CONTAINS(
                      remote_resource.applicable_event_types,
                      JSON_QUOTE(e.event_type)
                  )
                 AND remote_resource.city_code <> e.event_city_code
                 AND remote_resource.resource_status = 0
                 AND (remote_resource.del_flag IS NULL
                      OR remote_resource.del_flag IN ('N','0'))
                 AND remote_resource.available_quantity
                     > remote_resource.minimum_reserve_quantity
                LEFT JOIN w_emergency_resource local_resource
                  ON local_resource.resource_type_code = remote_resource.resource_type_code
                 AND local_resource.city_code = e.event_city_code
                 AND local_resource.resource_status = 0
                 AND (local_resource.del_flag IS NULL
                      OR local_resource.del_flag IN ('N','0'))
                WHERE e.c_type = '4' AND e.status = '1' AND e.completed = 0
                  AND e.deleted = 0 AND e.event_type IS NOT NULL
                  AND e.event_city_code IS NOT NULL
                  AND (w.workflow_id IS NULL OR (
                      w.current_stage = 1 AND w.workflow_status IN (0, 1, 2, 5, 6)
                  ))
                  AND local_resource.resource_id IS NULL
                ORDER BY e.c_no, remote_resource.resource_type_code
                LIMIT 1
                """);
        String eventId = candidate.get("event_id").toString();
        proposal(candidate.get("resource_type_code").toString(), 1, "验证跨市资源调配");

        var plan = service.generate(eventId);
        assertFalse(plan.hasResourceShortage());
        // The versioned response plan also adds mandatory baseline resources; only
        // the selected type is deliberately unavailable in the local city.
        var crossCityResources = plan.allocatedResources().stream()
                .filter(item -> item.resourceTypeCode().equals(candidate.get("resource_type_code").toString()))
                .toList();
        assertFalse(crossCityResources.isEmpty());
        assertTrue(crossCityResources.stream().allMatch(item -> item.dispatchScope() == DispatchScope.CROSS_CITY));
        plan.allocatedResources().forEach(item -> {
            var quantities = jdbc.queryForMap("""
                    SELECT available_quantity, minimum_reserve_quantity
                    FROM w_emergency_resource WHERE resource_id = ?
                    """, item.resourceId());
            int available = ((Number) quantities.get("available_quantity")).intValue();
            assertTrue(available >= 0);
            if (item.dispatchScope() == DispatchScope.CROSS_CITY) {
                assertTrue(available >= ((Number) quantities.get("minimum_reserve_quantity")).intValue());
            }
        });

        var published = publish(plan.planId(), eventId, false);
        assertEquals(WorkflowStatus.PUBLISHED, published.workflow().status());
        assertTrue(jdbc.queryForObject("""
                SELECT COUNT(*) FROM w_emergency_resource_allocation
                WHERE workflow_id = ? AND allocation_status = 1
                """, Integer.class, published.workflow().workflowId()) > 0);

        var released = service.releaseResources(new ReleaseResourcesCommand(
                published.workflow().workflowId(), "集成测试：处置完成后归队",
                published.workflow().lockVersion(), key("release")
        ));
        assertTrue(released.resourcesReleased());
        assertEquals(0, jdbc.queryForObject("""
                SELECT COUNT(*) FROM w_emergency_resource_allocation
                WHERE workflow_id = ? AND allocation_status IN (0, 1)
                """, Integer.class, published.workflow().workflowId()));
    }

    @Test
    @Transactional
    void shouldPersistProvincialShortageThroughConditionalApprovalAndRelease() {
        String eventId = jdbc.queryForObject("""
                SELECT e.c_no
                FROM w_lw_incident e
                LEFT JOIN w_emergency_dispatch_workflow w ON w.event_id = e.c_no
                WHERE e.c_type = '4' AND e.status = '1' AND e.completed = 0
                  AND e.deleted = 0 AND e.event_type = 'ET101'
                  AND (w.workflow_id IS NULL OR (
                      w.current_stage = 1 AND w.workflow_status IN (0, 1, 2, 5, 6)
                  ))
                ORDER BY e.c_no LIMIT 1
                """, String.class);
        proposal("WARNING_EQUIPMENT", 999, "建立交通警戒和分流区");

        var plan = service.generate(eventId);
        assertTrue(plan.hasResourceShortage());
        assertEquals(999, plan.resourceShortages().get(0).requiredQuantity());
        assertTrue(plan.resourceShortages().get(0).shortageQuantity() > 0);

        var published = publish(plan.planId(), eventId, true);
        assertEquals(1, published.commandDecision().noticeSnapshot()
                .resourceShortages().size());
        var released = service.releaseResources(new ReleaseResourcesCommand(
                published.workflow().workflowId(), "集成测试：资源全部归还",
                published.workflow().lockVersion(), key("gap-release")
        ));
        assertTrue(released.resourcesReleased());
    }

    private cn.fj.roadagent.application.dispatch.EmergencyWorkflowView publish(
            String planId, String eventId, boolean hasShortage
    ) {
        String workflowId = jdbc.queryForObject("""
                SELECT workflow_id FROM w_emergency_dispatch_workflow
                WHERE plan_id = ? AND event_id = ?
                """, String.class, planId, eventId);
        var workflow = service.getWorkflow(workflowId);
        var level2 = service.decideLevel1(new Level1DecisionCommand(
                workflow.workflow().workflowId(), Level1Decision.SUBMIT, "现场确认上报",
                workflow.workflow().lockVersion(), key("l1")
        ));
        var level3 = service.review(new ProfessionalReviewCommand(
                workflow.workflow().workflowId(), ApprovalDecision.APPROVE,
                EventSeverity.LARGER,
                hasShortage ? ResourceFeasibility.FEASIBLE_WITH_GAP : ResourceFeasibility.FEASIBLE,
                "影响道路正常通行",
                hasShortage ? "协调后续批次资源和替代措施" : "协调属地交通管制",
                hasShortage ? "存在缺口但可先期执行" : "资源方案可行",
                level2.workflow().lockVersion(), key("l2")
        ));
        return service.decideCommand(new CommandDecisionCommand(
                workflow.workflow().workflowId(), ApprovalDecision.APPROVE,
                hasShortage ? "同意先期执行并继续协调资源缺口" : "同意发布",
                level3.workflow().lockVersion(), key("l3")
        ));
    }

    private void proposal(String typeCode, int quantity, String purpose) {
        when(chatModel.generateStructured(
                any(ModelRequest.class), eq(DispatchPlanProposal.class)
        )).thenReturn(new DispatchPlanProposal(
                List.of(new DispatchPlanProposal.ProposedResource(typeCode, quantity, purpose)),
                "设置安全警戒，组织交通分流，实施现场处置并持续报送进展。"
        ));
    }

    private String key(String action) {
        return "it-" + action + "-" + UUID.randomUUID();
    }

    private DatabaseSnapshot snapshot() {
        return new DatabaseSnapshot(
                scalar("SELECT COUNT(*) FROM w_emergency_dispatch_order"),
                scalar("SELECT COUNT(*) FROM w_emergency_dispatch_workflow"),
                scalar("SELECT COUNT(*) FROM w_emergency_professional_review"),
                scalar("SELECT COUNT(*) FROM w_emergency_command_decision"),
                scalar("SELECT COUNT(*) FROM w_emergency_dispatch_action_log"),
                scalar("SELECT COUNT(*) FROM w_emergency_resource_allocation"),
                scalar("SELECT COALESCE(SUM(completed), 0) FROM w_lw_incident"),
                scalar("SELECT COALESCE(SUM(available_quantity), 0) FROM w_emergency_resource"),
                scalar("SELECT COALESCE(SUM(reserved_quantity), 0) FROM w_emergency_resource"),
                scalar("SELECT COALESCE(SUM(dispatched_quantity), 0) FROM w_emergency_resource"),
                scalar("SELECT COALESCE(SUM(lock_version), 0) FROM w_emergency_resource")
        );
    }

    private long scalar(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private record DatabaseSnapshot(
            long orders, long workflows, long reviews, long decisions, long actions,
            long allocations, long eventCompletedChecksum, long available, long reserved,
            long dispatched, long resourceLockChecksum
    ) {
    }
}
