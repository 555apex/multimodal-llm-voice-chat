package cn.fj.roadagent.core.maintenance;

import cn.fj.roadagent.application.agent.AgentIntent;
import cn.fj.roadagent.application.maintenance.*;
import cn.fj.roadagent.application.model.ModelRequest;
import cn.fj.roadagent.application.port.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceServiceTest {
    @Test void buildsStableReportAndReusesSnapshotWhenModelFails() {
        FakeRepository repository = new FakeRepository();
        MaintenanceService service = new MaintenanceService(repository, document -> new byte[]{1,2,3},
                new FailingModel(), Clock.fixed(Instant.parse("2026-10-05T02:00:00Z"), ZoneOffset.UTC));

        MaintenanceDocument first = service.document(AgentIntent.MAINTENANCE_REPORT, "生成2025年度养护统计分析报告");
        MaintenanceDocument second = service.document(AgentIntent.MAINTENANCE_REPORT, "生成2025年度养护统计分析报告");

        assertEquals(2025, first.reportYear());
        assertEquals(first.documentId(), second.documentId());
        assertTrue(first.sections().stream().flatMap(section -> section.rows().stream())
                .map(Object::toString).anyMatch(value -> value.contains("G104")));
        assertFalse(first.summary().contains("模型"));
        assertArrayEquals(new byte[]{1,2,3}, service.download(first.documentId()));
    }

    @Test void schemeRequiresARealProjectReference() {
        FakeRepository repository = new FakeRepository();
        MaintenanceService service = new MaintenanceService(repository, document -> new byte[0],
                new FailingModel(), Clock.systemUTC());
        assertTrue(service.selectedProject("比较G104养护方案").isPresent());
        assertTrue(service.selectedProject("比较一个没有指定的项目").isEmpty());
    }

    @Test void higherUrgencyPhraseDoesNotApplyContradictoryFilters() {
        FakeRepository repository = new FakeRepository();
        MaintenanceService service = new MaintenanceService(repository, document -> new byte[0],
                new FailingModel(), Clock.systemUTC());

        MaintenanceProjectResult result = service.projects("筛选紧急程度较高的修复养护项目");

        assertEquals(1, result.projects().size());
        assertEquals("较高", result.projects().get(0).urgency());
    }

    private static final class FakeRepository implements MaintenanceRepository {
        private final List<MaintenanceProject> projects = List.of(new MaintenanceProject(
                "MR-001", "G104", "北京-平潭", "FJ001-FJ010", "路面", "修复养护", "中修",
                "表面裂缝", "综合修复", "较高", 80, LocalDate.of(2026,10,20), 8,
                "测试养护单位", new BigDecimal("120.00")));
        private final Map<String, MaintenanceDocument> documents = new HashMap<>();
        @Override public void refreshProjects() {}
        @Override public List<MaintenanceProject> findActiveProjects() { return projects; }
        @Override public Optional<MaintenanceProject> findProject(String message) { return message.contains("G104") ? Optional.of(projects.get(0)) : Optional.empty(); }
        @Override public String currentBatchId() { return "batch"; }
        @Override public String loadTemplate(String templateKey) { return "固定模板"; }
        @Override public Optional<MaintenanceDocument> findSnapshot(String type, int year, String projectCode, String batchId) {
            return documents.values().stream().filter(d -> d.documentType().equals(type) && d.reportYear()==year
                    && Objects.equals(d.projectCode(), projectCode)).findFirst();
        }
        @Override public MaintenanceDocument saveSnapshot(MaintenanceDocument document) { documents.put(document.documentId(), document); return document; }
        @Override public Optional<MaintenanceDocument> findDocument(String documentId) { return Optional.ofNullable(documents.get(documentId)); }
    }

    private static final class FailingModel implements ChatModelPort {
        @Override public cn.fj.roadagent.application.model.ModelResponse generate(ModelRequest request) { throw new IllegalStateException("offline"); }
        @Override public <T> T generateStructured(ModelRequest request, Class<T> resultType) { throw new IllegalStateException("offline"); }
        @Override public void stream(ModelRequest request, cn.fj.roadagent.application.model.ModelStreamListener listener) { throw new IllegalStateException("offline"); }
    }
}
