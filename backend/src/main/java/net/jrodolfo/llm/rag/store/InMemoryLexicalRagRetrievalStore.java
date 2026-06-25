package net.jrodolfo.llm.rag.store;

import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagMatch;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code InMemoryLexicalRagRetrievalStore} is a lightweight, in-memory retrieval implementation used for
 * Retrieval-Augmented Generation (RAG).
 * <p>
 * Instead of using embeddings or an external vector database, it implements a lexical search mechanism using
 * <b>Term Frequency (TF)</b> and <b>Cosine Similarity</b>. It provides a simple, dependency-free way
 * to perform keyword-based retrieval, which is efficient for smaller datasets and requires
 * no external infrastructure.
 * </p>
 *
 * <h3>Key Responsibilities:</h3>
 * <ul>
 *     <li>Text Tokenization and Preprocessing: Cleans and splits text into searchable tokens.</li>
 *     <li>Data Storage: Maintains an in-memory index of document chunks and their term frequencies.</li>
 *     <li>Similarity Search: Uses Cosine Similarity to find the most relevant chunks for a given query.</li>
 * </ul>
 */
@Component
public class InMemoryLexicalRagRetrievalStore implements RagRetrievalStore {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern FENCED_CODE_BLOCK = Pattern.compile("(?s)```.*?```");
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[([^]]+)]\\([^)]*\\)");
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`]*`");
    private static final Pattern FILE_NAME = Pattern.compile("\\b[\\w.-]+\\.(?:java|jsx|md|ts|tsx|js|json|sh|yml|yaml|css|html)\\b", Pattern.CASE_INSENSITIVE);
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if", "in",
            "into", "is", "it", "of", "on", "or", "that", "the", "their", "then", "there",
            "these", "this", "to", "was", "will", "with", "your", "what", "which", "who", "why",
            "how", "when", "where", "using", "use", "used", "uses", "project", "system"
    );

    private volatile List<IndexedChunk> indexedChunks = List.of();
    private volatile Map<String, Double> inverseDocumentFrequencies = Map.of();

    /**
     * Rebuilds the entire index with the provided chunks.
     * Each chunk is processed to calculate its term frequencies before being stored.
     *
     * @param chunks the list of {@link RagChunk} to be indexed.
     */
    @Override
    public void replaceAll(List<RagChunk> chunks) {
        List<IndexedChunk> rebuiltChunks = chunks.stream()
                .map(chunk -> new IndexedChunk(chunk, tokenFrequencies(chunk.text())))
                .toList();
        indexedChunks = rebuiltChunks;
        inverseDocumentFrequencies = inverseDocumentFrequencies(rebuiltChunks);
    }

    /**
     * Searches for the most relevant chunks based on the provided query.
     * <p>
     * The search process involves:
     * <ol>
     *     <li>Converting the query into a frequency map.</li>
     *     <li>Calculating the Cosine Similarity between the query and each indexed chunk.</li>
     *     <li>Filtering out non-matching results (score &gt; 0).</li>
     *     <li>Sorting matches by score in descending order.</li>
     * </ol>
     * </p>
     *
     * @param query the search query string.
     * @param topK  the maximum number of top results to return.
     * @return a list of {@link RagMatch} sorted by relevance.
     */
    @Override
    public List<RagMatch> search(String query, int topK) {
        Map<String, Integer> queryVector = tokenFrequencies(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }
        return indexedChunks.stream()
                .map(indexedChunk -> new RagMatch(indexedChunk.chunk(), cosineSimilarity(
                        weightedVector(queryVector, inverseDocumentFrequencies),
                        weightedVector(indexedChunk.termFrequencies(), inverseDocumentFrequencies)
                )))
                .filter(match -> match.score() > 0.0d)
                .sorted(Comparator.comparingDouble(RagMatch::score).reversed())
                .limit(topK)
                .toList();
    }

    /**
     * Calculates the frequency of each token in the given text.
     *
     * @param text the text to process.
     * @return a map where keys are tokens and values are their respective frequencies.
     */
    private static Map<String, Integer> tokenFrequencies(String text) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String token : tokenize(searchableText(text))) {
            frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }

    private static String searchableText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String withoutCodeBlocks = FENCED_CODE_BLOCK.matcher(text).replaceAll(" ");
        String withoutLinkTargets = MARKDOWN_LINK.matcher(withoutCodeBlocks).replaceAll("$1");
        String withoutInlineCode = INLINE_CODE.matcher(withoutLinkTargets).replaceAll(" ");
        return FILE_NAME.matcher(withoutInlineCode).replaceAll(" ");
    }

    /**
     * Prepares text for indexing and searching.
     * <p>
     * This involves:
     * <ul>
     *     <li>Converting text to lowercase.</li>
     *     <li>Splitting it into alphanumeric tokens.</li>
     *     <li>Removing common "stop words" that don't add semantic value.</li>
     * </ul>
     * </p>
     *
     * @param text the raw text to tokenize.
     * @return a list of tokens.
     */
    private static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String rawToken : TOKEN_SPLIT.split(text.toLowerCase(Locale.ROOT))) {
            if (rawToken.isBlank() || STOP_WORDS.contains(rawToken)) {
                continue;
            }
            tokens.add(rawToken);
        }
        return tokens;
    }

    /**
     * Calculates the Cosine Similarity between two frequency vectors.
     * <p>
     * The similarity is calculated as the dot product of the vectors divided by the product
     * of their magnitudes. This normalizes the score by the "length" of the vectors, ensuring
     * that longer documents aren't unfairly prioritized.
     * </p>
     *
     * @param left  the first vector (e.g., query).
     * @param right the second vector (e.g., document chunk).
     * @return a similarity score between 0.0 (no match) and 1.0 (perfect match).
     */
    private static Map<String, Double> inverseDocumentFrequencies(List<IndexedChunk> chunks) {
        Map<String, Integer> documentFrequencies = new LinkedHashMap<>();
        for (IndexedChunk chunk : chunks) {
            for (String token : chunk.termFrequencies().keySet()) {
                documentFrequencies.merge(token, 1, Integer::sum);
            }
        }
        Map<String, Double> weights = new LinkedHashMap<>();
        int documentCount = chunks.size();
        for (Map.Entry<String, Integer> entry : documentFrequencies.entrySet()) {
            double idf = Math.log((documentCount + 1.0d) / (entry.getValue() + 1.0d)) + 1.0d;
            weights.put(entry.getKey(), idf);
        }
        return weights;
    }

    private static Map<String, Double> weightedVector(Map<String, Integer> frequencies, Map<String, Double> inverseDocumentFrequencies) {
        Map<String, Double> weighted = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            double weight = inverseDocumentFrequencies.getOrDefault(entry.getKey(), 1.0d);
            weighted.put(entry.getKey(), entry.getValue() * weight);
        }
        return weighted;
    }

    private static double cosineSimilarity(Map<String, Double> left, Map<String, Double> right) {
        double dotProduct = 0.0d;
        for (Map.Entry<String, Double> entry : left.entrySet()) {
            dotProduct += entry.getValue() * right.getOrDefault(entry.getKey(), 0.0d);
        }
        if (dotProduct == 0.0d) {
            return 0.0d;
        }
        return dotProduct / (vectorMagnitude(left.values()) * vectorMagnitude(right.values()));
    }

    /**
     * Calculates the size (Euclidean norm) of a vector represented as a collection of values.
     *
     * @param values The values in the vector.
     * @return The size of the vector.
     */
    private static double vectorMagnitude(Collection<Double> values) {
        double sum = 0.0d;
        for (Double value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    /**
     * Internal record representing a chunk along with its pre-calculated term frequencies.
     *
     * @param chunk           The original {@link RagChunk}.
     * @param termFrequencies A map of tokens to their frequencies in the chunk's text.
     */
    private record IndexedChunk(
            RagChunk chunk,
            Map<String, Integer> termFrequencies
    ) {
    }
}
