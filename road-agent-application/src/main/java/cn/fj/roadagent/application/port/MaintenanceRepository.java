package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.maintenance.MaintenanceDocument;
import cn.fj.roadagent.application.maintenance.MaintenanceProject;

import java.util.List;
import java.util.Optional;

public interface MaintenanceRepository {
    void refreshProjects();
    List<MaintenanceProject> findActiveProjects();
    Optional<MaintenanceProject> findProject(String userMessage);
    String currentBatchId();
    String loadTemplate(String templateKey);
    Optional<MaintenanceDocument> findSnapshot(String type, int year, String projectCode, String batchId);
    MaintenanceDocument saveSnapshot(MaintenanceDocument document);
    Optional<MaintenanceDocument> findDocument(String documentId);
}
