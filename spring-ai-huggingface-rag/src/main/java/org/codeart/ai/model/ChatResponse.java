package org.codeart.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for chat/question-answering endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {

    /**
     * The generated answer from the RAG pipeline.
     */
    private String answer;

    /**
     * List of source documents used to generate the answer.
     */
    private List<SourceDocument> sources;

    /**
     * Metadata about the RAG process.
     */
    private RagMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceDocument {
        private String content;
        private String documentId;
        private String fileName;
        private Double similarityScore;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagMetadata {
        private int documentsRetrieved;
        private long retrievalTimeMs;
        private long generationTimeMs;
        private String model;
    }
}
