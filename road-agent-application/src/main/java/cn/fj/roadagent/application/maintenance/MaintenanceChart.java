package cn.fj.roadagent.application.maintenance;

import java.util.List;

public record MaintenanceChart(String title, String unit, List<String> labels, List<Double> values) {
    public MaintenanceChart {
        labels = labels == null ? List.of() : List.copyOf(labels);
        values = values == null ? List.of() : List.copyOf(values);
    }
}
