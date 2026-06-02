package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagDocument;
import net.jrodolfo.llm.rag.store.InMemoryLexicalRagRetrievalStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagRetrievalServiceTest {

    @Test
    void retrieveReturnsMostRelevantChunksFirst() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("architecture.md", "Architecture", "The provider registry selects Ollama, Bedrock, or Hugging Face for each request."),
                document("sessions.md", "Sessions", "Sessions are stored as local JSON files for persistence and export.")
        ));

        var matches = retrievalService.retrieve("How does the provider registry work?");

        assertEquals("architecture.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void partialOverlapRanksRelevantChunkFirst() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("providers.md", "Providers", "Provider switching uses configured Ollama Bedrock and Hugging Face runtimes."),
                document("artifacts.md", "Artifacts", "Artifact preview shows report files and summary files from tool runs.")
        ));

        var matches = retrievalService.retrieve("configured provider runtimes");

        assertEquals("providers.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void stopWordHeavyQueryStillUsesMeaningfulTerms() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("sessions.md", "Sessions", "Sessions are persisted as local JSON files and can be exported."),
                document("providers.md", "Providers", "Providers are selected at runtime from configured model backends.")
        ));

        var matches = retrievalService.retrieve("the and of to where are sessions in this app");

        assertEquals("sessions.md", matches.getFirst().chunk().sourcePath());
        assertEquals(1, matches.size());
    }

    @Test
    void irrelevantQueryReturnsNoMatches() {
        RagRetrievalService retrievalService = retrievalService(3, List.of(
                document("sessions.md", "Sessions", "Sessions are persisted as local JSON files."),
                document("providers.md", "Providers", "Providers are selected at runtime.")
        ));

        var matches = retrievalService.retrieve("kubernetes ingress autoscaling cluster");

        assertEquals(0, matches.size());
    }

    @Test
    void topKLimitsReturnedMatches() {
        RagRetrievalService retrievalService = retrievalService(2, List.of(
                document("provider-registry.md", "Provider Registry", "Provider registry provider registry provider registry selects runtime models."),
                document("provider-status.md", "Provider Status", "Provider status checks provider health and provider model availability."),
                document("provider-docs.md", "Provider Docs", "Provider docs explain configured providers.")
        ));

        var matches = retrievalService.retrieve("provider");

        assertEquals(2, matches.size());
        List<String> sourcePaths = matches.stream()
                .map(match -> match.chunk().sourcePath())
                .toList();
        assertTrue(sourcePaths.contains("provider-registry.md"));
        assertTrue(sourcePaths.contains("provider-status.md"));
    }

    private static RagRetrievalService retrievalService(int topK, List<RagDocument> documents) {
        RagProperties properties = new RagProperties(true, "docs", 220, 30, topK, "lexical");
        InMemoryLexicalRagRetrievalStore store = new InMemoryLexicalRagRetrievalStore();
        RagCorpusService corpusService = new RagCorpusService(
                properties,
                new StubLoader(documents),
                new RagChunkingService(),
                store
        );
        return new RagRetrievalService(properties, corpusService, store);
    }

    private static RagDocument document(String path, String title, String content) {
        return new RagDocument(Path.of(path), title, content);
    }

    private static final class StubLoader extends RagDocumentLoader {
        private final List<RagDocument> documents;

        private StubLoader(List<RagDocument> documents) {
            this.documents = documents;
        }

        @Override
        public List<RagDocument> loadMarkdownDocuments(Path corpusRoot) {
            return documents;
        }
    }
}
