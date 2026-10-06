package cn.fj.roadagent.application.maintenance;

import java.time.Instant;
import java.util.List;

public record MaintenanceDocument(
        String documentId,
        String documentType,
        int reportYear,
        String projectCode,
        String title,
        String summary,
        String conclusion,
        List<MaintenanceSection> sections,
        List<MaintenanceChart> charts,
        String sourceBatchId,
        int version,
        Instant generatedAt,
        String downloadUrl
) {
    public MaintenanceDocument {
        sections = sections == null ? List.of() : List.copyOf(sections);
        charts = charts == null ? List.of() : List.copyOf(charts);
    }

    public MaintenanceDocument withDownloadUrl(String url) {
        return new MaintenanceDocument(documentId, documentType, reportYear, projectCode, title, summary,
                conclusion, sections, charts, sourceBatchId, version, generatedAt, url);
    }
}
