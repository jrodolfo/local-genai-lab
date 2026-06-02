package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.store.InMemoryLexicalRagRetrievalStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RagRetrievalServiceTest {

    @Test
    void retrieveReturnsMostRelevantChunksFirst() {
        RagProperties properties = new RagProperties(true, "docs", 220, 30, 3, "lexical");
        InMemoryLexicalRagRetrievalStore store = new InMemoryLexicalRagRetrievalStore();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(),
                new RagChunkingService(),
                store
        );
        RagRetrievalService retrievalService = new RagRetrievalService(properties, corpusService, store);

        var matches = retrievalService.retrieve("How does the provider registry work?");

        assertEquals("architecture.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    private static final class StubLoader extends RagDocumentLoader {
        @Override
        public List<RagDocument> loadMarkdownDocuments(Path corpusRoot) {
            return List.of(
                    new RagDocument(
                            Path.of("architecture.md"),
                            "Architecture",
                            "The provider registry selects Ollama, Bedrock, or Hugging Face for each request."
                    ),
                    new RagDocument(
                            Path.of("sessions.md"),
                            "Sessions",
                            "Sessions are stored as local JSON files for persistence and export."
                    )
            );
        }
    }
}
