package org.codeart.ai.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ai.model.ChatRequest;
import org.codeart.ai.model.ChatResponse;
import org.codeart.ai.service.RagService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * REST controller for RAG-based chat operations.
 * Provides endpoints for question-answering with context retrieval.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    /**
     * Ask a question and get a RAG-enhanced answer.
     *
     * @param request the chat request with question
     * @return response with answer and sources
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getQuestion());

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ChatResponse.builder()
                            .answer("Please provide a question.")
                            .build());
        }

        ChatResponse response = ragService.chat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Stream a RAG-enhanced answer as Server-Sent Events.
     *
     * @param request the chat request with question
     * @return flux of answer chunks
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequest request) {
        log.info("Received streaming chat request: {}", request.getQuestion());

        if (request.getQuestion() == null || request.getQuestion().isBlank()) {
            return Flux.just("Please provide a question.");
        }

        return ragService.chatStream(request);
    }

    /**
     * Simple GET endpoint for quick testing.
     *
     * @param question the question to ask
     * @param topK     number of documents to retrieve (default: 5)
     * @return response with answer
     */
    @GetMapping
    public ResponseEntity<ChatResponse> askQuestion(
            @RequestParam String question,
            @RequestParam(defaultValue = "5") int topK) {

        ChatRequest request = ChatRequest.builder()
                .question(question)
                .topK(topK)
                .build();

        return chat(request);
    }
}
