package org.codeart.ai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for ingesting documents into the vector store.
 * Handles document loading, chunking, and embedding generation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final DocumentTransformer tokenTextSplitter;

    @Value("${app.document.upload-dir}")
    private String uploadDir;

    /**
     * Ingest a document file into the vector store.
     *
     * @param file the uploaded document file
     * @return number of chunks created and stored
     * @throws IOException if file processing fails
     */
    public int ingestDocument(MultipartFile file) throws IOException {
        String fileName = file.getOriginalFilename();
        String documentId = UUID.randomUUID().toString();

        log.info("Ingesting document: {} with ID: {}", fileName, documentId);

        // Save file temporarily
        Path tempFile = saveToTempFile(file);

        try {
            // Load and parse document
            List<Document> documents = loadDocument(tempFile, fileName);

            // Add metadata to documents - need to create new documents with updated
            // metadata
            List<Document> enrichedDocs = documents.stream()
                    .map(doc -> {
                        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                        metadata.put("documentId", documentId);
                        metadata.put("fileName", fileName);
                        metadata.put("source", "upload");
                        return new Document(doc.getText(), metadata);
                    })
                    .collect(Collectors.toList());

            // Split into chunks
            List<Document> chunks = tokenTextSplitter.apply(enrichedDocs);
            log.info("Created {} chunks from document: {}", chunks.size(), fileName);

            // Add chunks to vector store (embeddings are generated automatically)
            vectorStore.add(chunks);
            log.info("Successfully ingested {} chunks into vector store", chunks.size());

            return chunks.size();

        } finally {
            // Cleanup temp file
            Files.deleteIfExists(tempFile);
        }
    }

    /**
     * Ingest document from a URL or resource.
     *
     * @param resource the resource to ingest
     * @param fileName the name to associate with the document
     * @return number of chunks created
     */
    public int ingestDocument(Resource resource, String fileName) {
        String documentId = UUID.randomUUID().toString();
        log.info("Ingesting resource: {} with ID: {}", fileName, documentId);

        List<Document> documents = loadDocumentFromResource(resource, fileName);

        List<Document> enrichedDocs = documents.stream()
                .map(doc -> {
                    Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                    metadata.put("documentId", documentId);
                    metadata.put("fileName", fileName);
                    metadata.put("source", "resource");
                    return new Document(doc.getText(), metadata);
                })
                .collect(Collectors.toList());

        List<Document> chunks = tokenTextSplitter.apply(enrichedDocs);
        vectorStore.add(chunks);

        log.info("Successfully ingested {} chunks from resource", chunks.size());
        return chunks.size();
    }

    /**
     * Delete documents from the vector store by document ID.
     *
     * @param documentId the document ID to delete
     */
    public void deleteDocument(String documentId) {
        log.info("Deleting document with ID: {}", documentId);
        vectorStore.delete(List.of(documentId));
    }

    private Path saveToTempFile(MultipartFile file) throws IOException {
        Path uploadPath = Path.of(uploadDir);
        Files.createDirectories(uploadPath);

        Path tempFile = uploadPath.resolve(UUID.randomUUID() + "_" + file.getOriginalFilename());
        try (InputStream is = file.getInputStream()) {
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private List<Document> loadDocument(Path filePath, String fileName) {
        Resource resource = new org.springframework.core.io.FileSystemResource(filePath.toFile());
        return loadDocumentFromResource(resource, fileName);
    }

    private List<Document> loadDocumentFromResource(Resource resource, String fileName) {
        String lowerFileName = fileName.toLowerCase();

        if (lowerFileName.endsWith(".pdf")) {
            log.debug("Loading PDF document: {}", fileName);
            PagePdfDocumentReader pdfReader = new PagePdfDocumentReader(resource);
            return pdfReader.read();

        } else if (lowerFileName.endsWith(".txt") || lowerFileName.endsWith(".md")) {
            log.debug("Loading text document: {}", fileName);
            TextReader textReader = new TextReader(resource);
            return textReader.read();

        } else {
            // Use Tika for other formats (docx, html, etc.)
            log.debug("Loading document with Tika: {}", fileName);
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            return tikaReader.read();
        }
    }
}
