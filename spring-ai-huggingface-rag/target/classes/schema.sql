-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- The vector_store table will be automatically created by Spring AI
-- This file is for reference and additional custom tables if needed

-- Optional: Create a documents metadata table for tracking
CREATE TABLE IF NOT EXISTS document_metadata (
    id VARCHAR(255) PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    file_size BIGINT,
    chunks_count INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for efficient lookups
CREATE INDEX IF NOT EXISTS idx_document_metadata_filename ON document_metadata(file_name);
CREATE INDEX IF NOT EXISTS idx_document_metadata_created ON document_metadata(created_at);
