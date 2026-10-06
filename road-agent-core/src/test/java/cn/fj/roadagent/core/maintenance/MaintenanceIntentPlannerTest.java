package cn.fj.roadagent.core.maintenance;

import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.ChatModelPort;
import cn.fj.roadagent.core.agent.IntentPlanner;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MaintenanceIntentPlannerTest {
    private final IntentPlanner planner = new IntentPlanner(new FailingModel(), null, true);

    @Test void recognizesProjectList() {
        assertEquals(AgentIntent.MAINTENANCE_PROJECT_LIST,
                planner.plan("目前有哪些需要养护的国省道路段？", List.of()).parsedIntent());
    }

    @Test void recognizesPreplan() {
        assertEquals(AgentIntent.MAINTENANCE_PREPLAN,
                planner.plan("生成本年度养护预安排计划", List.of()).parsedIntent());
    }

    @Test void recognizesSchemeComparison() {
        assertEquals(AgentIntent.MAINTENANCE_SCHEME_COMPARISON,
                planner.plan("比较G104某项目的三种养护方案", List.of()).parsedIntent());
    }

    @Test void recognizesAllReportFamilies() {
        assertEquals(AgentIntent.MAINTENANCE_REPORT, planner.plan("生成2025年度养护统计分析报告", List.of()).parsedIntent());
        assertEquals(AgentIntent.MAINTENANCE_REPORT, planner.plan("生成路网病害分布分析报告", List.of()).parsedIntent());
        assertEquals(AgentIntent.MAINTENANCE_REPORT, planner.plan("生成年度大中修工程考核评估报告", List.of()).parsedIntent());
        assertEquals(AgentIntent.MAINTENANCE_REPORT, planner.plan("生成日常养护量化评分报告", List.of()).parsedIntent());
        assertEquals(AgentIntent.MAINTENANCE_REPORT, planner.plan("生成服务站服务质量满意度评估报告", List.of()).parsedIntent());
    }

    @Test void leavesMaintenancePolicyToRag() {
        assertEquals(AgentIntent.KNOWLEDGE_QA,
                planner.plan("公路养护工程管理办法对设计有什么要求？", List.of()).parsedIntent());
    }

    private static final class FailingModel implements ChatModelPort {
        @Override public cn.fj.roadagent.application.model.ModelResponse generate(ModelRequest request) { throw new AssertionError(); }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) {
            throw new AssertionError("deterministic maintenance routing must not call model");
        }
        @Override public void stream(ModelRequest request, cn.fj.roadagent.application.model.ModelStreamListener listener) { throw new AssertionError(); }
    }
}
