package cn.fj.roadagent.interfaces.rest.maintenance;

import cn.fj.roadagent.application.maintenance.MaintenanceDocumentUseCase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/maintenance/documents")
public final class MaintenanceDocumentController {
    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    private final MaintenanceDocumentUseCase documents;

    public MaintenanceDocumentController(MaintenanceDocumentUseCase documents) { this.documents = documents; }

    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(@PathVariable String documentId) {
        var document = documents.findDocument(documentId)
                .orElseThrow(() -> new IllegalArgumentException("养护文档不存在或已失效"));
        String safeName = document.title().replaceAll("[\\\\/:*?\"<>|]", "_") + ".docx";
        return ResponseEntity.ok().contentType(DOCX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(safeName, StandardCharsets.UTF_8).build().toString())
                .body(documents.download(documentId));
    }
}
