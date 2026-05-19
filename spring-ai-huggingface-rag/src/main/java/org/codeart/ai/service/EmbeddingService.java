package org.codeart.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for generating embeddings using Hugging Face models.
 * This is a thin wrapper around Spring AI's EmbeddingModel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * Generate embeddings for a single text.
     *
     * @param text the text to embed
     * @return embedding vector as float array
     */
    public float[] embed(String text) {
        log.debug("Generating embedding for text of length: {}", text.length());
        return embeddingModel.embed(text);
    }

    /**
     * Generate embeddings for multiple texts.
     *
     * @param texts list of texts to embed
     * @return embedding response containing all vectors
     */
    public EmbeddingResponse embedBatch(List<String> texts) {
        log.debug("Generating embeddings for {} texts", texts.size());
        return embeddingModel.embedForResponse(texts);
    }

    /**
     * Get the dimension of embeddings produced by the model.
     *
     * @return embedding dimension
     */
    public int getEmbeddingDimension() {
        return embeddingModel.dimensions();
    }
}
