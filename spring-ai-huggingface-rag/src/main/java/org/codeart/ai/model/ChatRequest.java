package org.codeart.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for chat/question-answering endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {

    /**
     * The user's question to be answered using RAG.
     */
    private String question;

    /**
     * Optional: Number of context documents to retrieve.
     * Default is 5 if not specified.
     */
    private Integer topK;

    /**
     * Optional: Similarity threshold for document retrieval.
     * Default is 0.7 if not specified.
     */
    private Double similarityThreshold;

    public int getTopKOrDefault() {
        return topK != null ? topK : 5;
    }

    public double getSimilarityThresholdOrDefault() {
        return similarityThreshold != null ? similarityThreshold : 0.7;
    }
}
