package org.codeart.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ai.model.DocumentResponse;
import org.codeart.ai.service.DocumentIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for document management operations.
 * Handles document upload, listing, and deletion.
 */
@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    /**
     * Upload and ingest a document into the vector store.
     *
     * @param file the document file to upload
     * @return response with ingestion details
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentResponse> uploadDocument(@RequestParam("file") MultipartFile file) {
        log.info("Received document upload request: {}", file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(DocumentResponse.builder()
                            .status("FAILED")
                            .message("File is empty")
                            .build());
        }

        String fileName = file.getOriginalFilename();
        if (!isValidFileType(fileName)) {
            return ResponseEntity.badRequest()
                    .body(DocumentResponse.builder()
                            .fileName(fileName)
                            .status("FAILED")
                            .message("Unsupported file type. Supported: PDF, TXT, MD, DOCX, HTML")
                            .build());
        }

        try {
            int chunksCreated = documentIngestionService.ingestDocument(file);

            return ResponseEntity.ok(DocumentResponse.builder()
                    .documentId(UUID.randomUUID().toString())
                    .fileName(fileName)
                    .chunksCreated(chunksCreated)
                    .status("SUCCESS")
                    .message("Document successfully ingested into vector store")
                    .processedAt(LocalDateTime.now())
                    .build());

        } catch (IOException e) {
            log.error("Failed to ingest document: {}", fileName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(DocumentResponse.builder()
                            .fileName(fileName)
                            .status("FAILED")
                            .message("Failed to process document: " + e.getMessage())
                            .build());
        }
    }

    /**
     * Upload multiple documents at once.
     *
     * @param files list of document files
     * @return list of ingestion responses
     */
    @PostMapping(value = "/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<DocumentResponse>> uploadDocuments(@RequestParam("files") List<MultipartFile> files) {
        log.info("Received batch upload request for {} files", files.size());

        List<DocumentResponse> responses = files.stream()
                .map(file -> {
                    try {
                        int chunks = documentIngestionService.ingestDocument(file);
                        return DocumentResponse.builder()
                                .documentId(UUID.randomUUID().toString())
                                .fileName(file.getOriginalFilename())
                                .chunksCreated(chunks)
                                .status("SUCCESS")
                                .message("Document ingested successfully")
                                .processedAt(LocalDateTime.now())
                                .build();
                    } catch (IOException e) {
                        log.error("Failed to process file: {}", file.getOriginalFilename(), e);
                        return DocumentResponse.builder()
                                .fileName(file.getOriginalFilename())
                                .status("FAILED")
                                .message("Error: " + e.getMessage())
                                .build();
                    }
                })
                .toList();

        return ResponseEntity.ok(responses);
    }

    /**
     * Delete a document from the vector store.
     *
     * @param documentId the document ID to delete
     * @return success response
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<DocumentResponse> deleteDocument(@PathVariable String documentId) {
        log.info("Deleting document: {}", documentId);

        documentIngestionService.deleteDocument(documentId);

        return ResponseEntity.ok(DocumentResponse.builder()
                .documentId(documentId)
                .status("DELETED")
                .message("Document removed from vector store")
                .processedAt(LocalDateTime.now())
                .build());
    }

    private boolean isValidFileType(String fileName) {
        if (fileName == null)
            return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".pdf") ||
                lower.endsWith(".txt") ||
                lower.endsWith(".md") ||
                lower.endsWith(".docx") ||
                lower.endsWith(".html") ||
                lower.endsWith(".htm");
    }
}
