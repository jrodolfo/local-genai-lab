package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.model.RagEmbeddedChunk;
import net.jrodolfo.llm.rag.model.RagMatch;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InMemoryVectorRagRetrievalStoreTest {

    @Test
    void searchByVectorRanksMostSimilarChunkFirst() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();
        store.replaceAllEmbedded(List.of(
                embeddedChunk("architecture.md#0", "architecture.md", "Architecture", "Provider registry docs.", List.of(1.0, 0.0)),
                embeddedChunk("providers.md#0", "providers.md", "Providers", "Provider configuration docs.", List.of(0.8, 0.2)),
                embeddedChunk("sessions.md#0", "sessions.md", "Sessions", "Session persistence docs.", List.of(0.0, 1.0))
        ));

        List<RagMatch> matches = store.searchByVector(List.of(1.0, 0.0), 3);

        assertEquals(2, matches.size());
        assertEquals("architecture.md#0", matches.get(0).chunk().id());
        assertEquals("providers.md#0", matches.get(1).chunk().id());
        assertEquals(1.0d, matches.get(0).score(), 0.00001d);
    }

    @Test
    void searchByVectorLimitsByTopK() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();
        store.replaceAllEmbedded(List.of(
                embeddedChunk("first.md#0", "first.md", "First", "First text.", List.of(1.0, 0.0)),
                embeddedChunk("second.md#0", "second.md", "Second", "Second text.", List.of(0.9, 0.1))
        ));

        List<RagMatch> matches = store.searchByVector(List.of(1.0, 0.0), 1);

        assertEquals(1, matches.size());
        assertEquals("first.md#0", matches.getFirst().chunk().id());
    }

    @Test
    void searchByVectorReturnsEmptyWhenIndexIsEmpty() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();

        List<RagMatch> matches = store.searchByVector(List.of(1.0, 0.0), 3);

        assertEquals(0, matches.size());
    }

    @Test
    void searchByVectorSafelyHandlesZeroQueryVector() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();
        store.replaceAllEmbedded(List.of(
                embeddedChunk("docs.md#0", "docs.md", "Docs", "Document text.", List.of(1.0, 0.0))
        ));

        List<RagMatch> matches = store.searchByVector(List.of(0.0, 0.0), 3);

        assertEquals(0, matches.size());
    }

    @Test
    void searchByVectorIgnoresZeroChunkVector() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();
        store.replaceAllEmbedded(List.of(
                embeddedChunk("zero.md#0", "zero.md", "Zero", "Zero vector.", List.of(0.0, 0.0)),
                embeddedChunk("match.md#0", "match.md", "Match", "Matching vector.", List.of(1.0, 0.0))
        ));

        List<RagMatch> matches = store.searchByVector(List.of(1.0, 0.0), 3);

        assertEquals(1, matches.size());
        assertEquals("match.md#0", matches.getFirst().chunk().id());
    }

    @Test
    void searchByVectorSkipsDimensionMismatches() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();
        store.replaceAllEmbedded(List.of(
                embeddedChunk("docs.md#0", "docs.md", "Docs", "Document text.", List.of(1.0, 0.0))
        ));

        List<RagMatch> matches = store.searchByVector(List.of(1.0, 0.0, 0.0), 3);

        assertEquals(0, matches.size());
    }

    @Test
    void replaceAllEmbeddedRejectsMixedVectorDimensions() {
        InMemoryVectorRagRetrievalStore store = new InMemoryVectorRagRetrievalStore();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> store.replaceAllEmbedded(List.of(
                embeddedChunk("first.md#0", "first.md", "First", "First text.", List.of(1.0, 0.0)),
                embeddedChunk("second.md#0", "second.md", "Second", "Second text.", List.of(1.0, 0.0, 0.0))
        )));

        assertEquals("All embedded chunks must have the same vector dimension.", exception.getMessage());
    }

    private static RagEmbeddedChunk embeddedChunk(
            String id,
            String sourcePath,
            String title,
            String text,
            List<Double> vector
    ) {
        return new RagEmbeddedChunk(id, sourcePath, title, text, "nomic-embed-text", vector.size(), vector);
    }
}
