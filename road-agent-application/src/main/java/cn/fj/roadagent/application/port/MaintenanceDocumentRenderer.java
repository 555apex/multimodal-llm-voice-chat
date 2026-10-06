package cn.fj.roadagent.application.port;

import cn.fj.roadagent.application.maintenance.MaintenanceDocument;

public interface MaintenanceDocumentRenderer {
    byte[] render(MaintenanceDocument document);
}
