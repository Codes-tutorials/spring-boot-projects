package org.codeart.ai.config;

import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for document processing and AI components.
 */
@Configuration
public class AiConfig {

    @Value("${app.document.chunk-size:1000}")
    private int chunkSize;

    @Value("${app.document.chunk-overlap:200}")
    private int chunkOverlap;

    /**
     * Token-based text splitter for chunking documents.
     * Splits documents into manageable chunks for embedding generation.
     */
    @Bean
    public DocumentTransformer tokenTextSplitter() {
        return new TokenTextSplitter(
                chunkSize, // default chunk size
                chunkOverlap, // min chunk size overlap
                5, // min chunk length
                10000, // max tokens
                true // keep separators
        );
    }
}
