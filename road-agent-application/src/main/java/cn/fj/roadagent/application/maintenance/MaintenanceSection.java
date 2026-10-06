package cn.fj.roadagent.application.maintenance;

import java.util.List;
import java.util.Map;

public record MaintenanceSection(
        String heading,
        List<String> paragraphs,
        List<Map<String, Object>> rows
) {
    public MaintenanceSection {
        paragraphs = paragraphs == null ? List.of() : List.copyOf(paragraphs);
        rows = rows == null ? List.of() : List.copyOf(rows);
    }
}
