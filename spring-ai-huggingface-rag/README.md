# Spring AI Hugging Face RAG

A production-ready Spring Boot application implementing **Retrieval-Augmented Generation (RAG)** using Spring AI with Hugging Face APIs for embeddings and chat completions.

## Features

- 🤗 **Hugging Face Integration** - Uses Hugging Face Inference API for embeddings and chat
- 📄 **Multi-format Document Support** - PDF, TXT, MD, DOCX, HTML
- 🔍 **Vector Search** - PostgreSQL with pgvector for efficient similarity search
- 💬 **RAG Chat API** - Context-aware question answering
- 📡 **Streaming Responses** - Server-Sent Events for real-time output
- 🐳 **Docker Ready** - Docker Compose setup for PostgreSQL

## Tech Stack

- Java 17+
- Spring Boot 3.3
- Spring AI 1.0
- PostgreSQL with pgvector
- Hugging Face Inference API

## Quick Start

### Prerequisites

1. **Java 17+** installed
2. **Docker** (for PostgreSQL with pgvector)
3. **Hugging Face API Key** - Get one from [huggingface.co/settings/tokens](https://huggingface.co/settings/tokens)

### Setup

1. **Start PostgreSQL with pgvector:**

```bash
docker-compose up -d
```

2. **Set your Hugging Face API key:**

```bash
# Windows
set HUGGINGFACE_API_KEY=your-api-key-here

# Linux/macOS
export HUGGINGFACE_API_KEY=your-api-key-here
```

3. **Run the application:**

```bash
mvn spring-boot:run
```

The application will start at `http://localhost:8080`

## API Endpoints

### Document Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/documents/upload` | Upload a single document |
| POST | `/api/documents/upload-batch` | Upload multiple documents |
| DELETE | `/api/documents/{id}` | Delete a document |

### Chat (RAG)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat` | Ask a question with RAG |
| POST | `/api/chat/stream` | Streaming RAG response (SSE) |
| GET | `/api/chat?question=...` | Quick question endpoint |

## Usage Examples

### Upload a Document

```bash
curl -X POST http://localhost:8080/api/documents/upload \
  -F "file=@document.pdf"
```

### Ask a Question

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "What is the main topic of this document?",
    "topK": 5,
    "similarityThreshold": 0.7
  }'
```

### Streaming Response

```bash
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"question": "Summarize the key points"}' \
  --no-buffer
```

## Configuration

Key configuration options in `application.yml`:

```yaml
spring:
  ai:
    huggingface:
      api-key: ${HUGGINGFACE_API_KEY}
      chat:
        options:
          model: mistralai/Mistral-7B-Instruct-v0.3
      embedding:
        options:
          model: sentence-transformers/all-MiniLM-L6-v2
    vectorstore:
      pgvector:
        dimensions: 384  # Must match embedding model output

app:
  document:
    chunk-size: 1000    # Tokens per chunk
    chunk-overlap: 200  # Overlap between chunks
```

## Hugging Face Models

### Recommended Models

**Chat Models:**
- `mistralai/Mistral-7B-Instruct-v0.3` (default)
- `meta-llama/Llama-2-7b-chat-hf`
- `HuggingFaceH4/zephyr-7b-beta`

**Embedding Models:**
- `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions, default)
- `sentence-transformers/all-mpnet-base-v2` (768 dimensions)
- `BAAI/bge-small-en-v1.5` (384 dimensions)

> **Note:** When changing embedding models, update the `dimensions` in pgvector config to match.

## Project Structure

```
spring-ai-huggingface-rag/
├── pom.xml
├── docker-compose.yml
├── README.md
└── src/main/
    ├── java/org/codeart/ai/
    │   ├── SpringAiRagApplication.java
    │   ├── config/
    │   │   └── AiConfig.java
    │   ├── controller/
    │   │   ├── ChatController.java
    │   │   └── DocumentController.java
    │   ├── model/
    │   │   ├── ChatRequest.java
    │   │   ├── ChatResponse.java
    │   │   └── DocumentResponse.java
    │   └── service/
    │       ├── DocumentIngestionService.java
    │       ├── EmbeddingService.java
    │       └── RagService.java
    └── resources/
        ├── application.yml
        └── schema.sql
```

## License

MIT License
