package org.codeart.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for document upload operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentResponse {

    /**
     * Unique identifier for the document.
     */
    private String documentId;

    /**
     * Original filename of the uploaded document.
     */
    private String fileName;

    /**
     * Number of chunks created from the document.
     */
    private int chunksCreated;

    /**
     * Status of the document processing.
     */
    private String status;

    /**
     * Message providing additional details.
     */
    private String message;

    /**
     * Timestamp when the document was processed.
     */
    private LocalDateTime processedAt;
}
