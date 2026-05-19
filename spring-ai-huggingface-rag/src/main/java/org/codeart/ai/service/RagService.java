package org.codeart.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ai.model.ChatRequest;
import org.codeart.ai.model.ChatResponse;
import org.codeart.ai.model.ChatResponse.RagMetadata;
import org.codeart.ai.model.ChatResponse.SourceDocument;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core RAG (Retrieval-Augmented Generation) service.
 * Combines vector store retrieval with LLM generation for context-aware
 * answers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

        private final VectorStore vectorStore;
        private final ChatModel chatModel;
        private final ChatClient.Builder chatClientBuilder;

        @Value("${spring.ai.huggingface.chat.options.model:mistralai/Mistral-7B-Instruct-v0.3}")
        private String modelName;

        private static final String RAG_PROMPT_TEMPLATE = """
                        You are a helpful AI assistant. Answer the user's question based ONLY on the following context.
                        If the context doesn't contain enough information to answer the question, say "I don't have enough information to answer this question based on the available documents."

                        Context:
                        {context}

                        Question: {question}

                        Answer:
                        """;

        /**
         * Process a question using RAG pipeline.
         *
         * @param request the chat request containing the question
         * @return response with answer and source documents
         */
        public ChatResponse chat(ChatRequest request) {
                log.info("Processing RAG query: {}", request.getQuestion());

                long startRetrieval = System.currentTimeMillis();

                // Step 1: Retrieve relevant documents from vector store
                List<Document> relevantDocs = retrieveDocuments(
                                request.getQuestion(),
                                request.getTopKOrDefault(),
                                request.getSimilarityThresholdOrDefault());

                long retrievalTime = System.currentTimeMillis() - startRetrieval;
                log.debug("Retrieved {} documents in {}ms", relevantDocs.size(), retrievalTime);

                if (relevantDocs.isEmpty()) {
                        return ChatResponse.builder()
                                        .answer("I couldn't find any relevant documents to answer your question. Please try uploading some documents first.")
                                        .sources(List.of())
                                        .metadata(RagMetadata.builder()
                                                        .documentsRetrieved(0)
                                                        .retrievalTimeMs(retrievalTime)
                                                        .generationTimeMs(0)
                                                        .model(modelName)
                                                        .build())
                                        .build();
                }

                // Step 2: Build context from retrieved documents
                String context = buildContext(relevantDocs);

                // Step 3: Generate answer using LLM
                long startGeneration = System.currentTimeMillis();
                String answer = generateAnswer(request.getQuestion(), context);
                long generationTime = System.currentTimeMillis() - startGeneration;

                log.debug("Generated answer in {}ms", generationTime);

                // Step 4: Build response with sources
                List<SourceDocument> sources = relevantDocs.stream()
                                .map(doc -> SourceDocument.builder()
                                                .content(truncateContent(doc.getText(), 200))
                                                .documentId((String) doc.getMetadata().get("documentId"))
                                                .fileName((String) doc.getMetadata().get("fileName"))
                                                .similarityScore((Double) doc.getMetadata().get("score"))
                                                .build())
                                .collect(Collectors.toList());

                return ChatResponse.builder()
                                .answer(answer)
                                .sources(sources)
                                .metadata(RagMetadata.builder()
                                                .documentsRetrieved(relevantDocs.size())
                                                .retrievalTimeMs(retrievalTime)
                                                .generationTimeMs(generationTime)
                                                .model(modelName)
                                                .build())
                                .build();
        }

        /**
         * Stream a RAG response for real-time output.
         *
         * @param request the chat request
         * @return flux of response chunks
         */
        public Flux<String> chatStream(ChatRequest request) {
                log.info("Processing streaming RAG query: {}", request.getQuestion());

                List<Document> relevantDocs = retrieveDocuments(
                                request.getQuestion(),
                                request.getTopKOrDefault(),
                                request.getSimilarityThresholdOrDefault());

                if (relevantDocs.isEmpty()) {
                        return Flux.just("I couldn't find any relevant documents to answer your question.");
                }

                String context = buildContext(relevantDocs);

                ChatClient chatClient = chatClientBuilder.build();

                return chatClient.prompt()
                                .user(u -> u.text(RAG_PROMPT_TEMPLATE)
                                                .param("context", context)
                                                .param("question", request.getQuestion()))
                                .stream()
                                .content();
        }

        private List<Document> retrieveDocuments(String query, int topK, double similarityThreshold) {
                // Use simple string-based similarity search
                List<Document> results = vectorStore.similaritySearch(query);
                // Limit results to topK
                return results.size() > topK ? results.subList(0, topK) : results;
        }

        private String buildContext(List<Document> documents) {
                StringBuilder context = new StringBuilder();
                for (int i = 0; i < documents.size(); i++) {
                        Document doc = documents.get(i);
                        context.append("--- Document ").append(i + 1).append(" ---\n");
                        context.append(doc.getText()).append("\n\n");
                }
                return context.toString();
        }

        private String generateAnswer(String question, String context) {
                PromptTemplate promptTemplate = new PromptTemplate(RAG_PROMPT_TEMPLATE);
                Prompt prompt = promptTemplate.create(Map.of(
                                "context", context,
                                "question", question));

                return chatModel.call(prompt).getResult().getOutput().getText();
        }

        private String truncateContent(String content, int maxLength) {
                if (content == null)
                        return "";
                if (content.length() <= maxLength)
                        return content;
                return content.substring(0, maxLength) + "...";
        }
}
