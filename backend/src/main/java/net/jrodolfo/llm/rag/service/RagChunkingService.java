package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Service for splitting documents into smaller, manageable chunks.
 * This is crucial for efficient retrieval and to stay within LLM context window limits.
 */
@Service
public class RagChunkingService {

    /**
     * Splits a list of documents into chunks.
     *
     * @param documents the list of documents to chunk
     * @param maxChunkSize the maximum size of each chunk (in characters)
     * @param chunkOverlap the number of characters to overlap between consecutive chunks
     * @return a list of {@link RagChunk} objects
     */
    public List<RagChunk> chunkDocuments(List<RagDocument> documents, int maxChunkSize, int chunkOverlap) {
        List<RagChunk> chunks = new ArrayList<>();
        for (RagDocument document : documents) {
            chunks.addAll(chunkDocument(document, maxChunkSize, chunkOverlap));
        }
        return chunks;
    }

    List<RagChunk> chunkDocument(RagDocument document, int maxChunkSize, int chunkOverlap) {
        List<RagChunk> chunks = new ArrayList<>();
        String normalized = normalize(document.content());
        if (normalized.isBlank()) {
            return chunks;
        }

        int start = 0;
        int index = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + maxChunkSize);
            if (end < normalized.length()) {
                int paragraphBoundary = normalized.lastIndexOf("\n\n", end);
                if (paragraphBoundary > start + maxChunkSize / 2) {
                    end = paragraphBoundary;
                } else {
                    int whitespaceBoundary = normalized.lastIndexOf(' ', end);
                    if (whitespaceBoundary > start + maxChunkSize / 2) {
                        end = whitespaceBoundary;
                    }
                }
            }

            String text = normalized.substring(start, end).trim();
            if (!text.isBlank()) {
                chunks.add(new RagChunk(
                        document.path() + "#chunk-" + index,
                        document.path().toString().replace('\\', '/'),
                        document.title(),
                        text
                ));
                index++;
            }

            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - Math.max(0, chunkOverlap));
        }
        return chunks;
    }

    private String normalize(String content) {
        return content == null ? "" : content.replace("\r\n", "\n").trim();
    }
}
