package cn.fj.roadagent.application.maintenance;

import java.time.Instant;
import java.util.List;

public record MaintenanceProjectResult(
        String title,
        String summary,
        List<MaintenanceProject> projects,
        String sourceBatchId,
        Instant generatedAt
) {
    public MaintenanceProjectResult {
        projects = projects == null ? List.of() : List.copyOf(projects);
    }
}
