package net.jrodolfo.llm.rag.embedding;

/**
 * Request body for Ollama's single-input embeddings endpoint.
 *
 * @param model  embedding model name
 * @param prompt text to embed
 */
record OllamaEmbeddingRequest(String model, String prompt) {
}
