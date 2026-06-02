package net.jrodolfo.llm.rag.embedding;

import java.util.List;

/**
 * Response body from Ollama's single-input embeddings endpoint.
 *
 * @param embedding numeric embedding vector
 */
record OllamaEmbeddingResponse(List<Double> embedding) {
}
