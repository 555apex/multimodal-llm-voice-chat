package cn.fj.roadagent.application.maintenance;

import java.util.Optional;

public interface MaintenanceDocumentUseCase {
    Optional<MaintenanceDocument> findDocument(String documentId);
    byte[] download(String documentId);
}
