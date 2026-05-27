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
import java.util.stream.Collectors;

@Component
public class InMemoryRagVectorStore implements RagVectorStore {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "but", "by", "for", "from", "if", "in",
            "into", "is", "it", "of", "on", "or", "that", "the", "their", "then", "there",
            "these", "this", "to", "was", "will", "with", "your"
    );

    private volatile List<IndexedChunk> indexedChunks = List.of();

    @Override
    public void replaceAll(List<RagChunk> chunks) {
        indexedChunks = chunks.stream()
                .map(chunk -> new IndexedChunk(chunk, tokenFrequencies(chunk.text())))
                .toList();
    }

    @Override
    public List<RagMatch> search(String query, int topK) {
        Map<String, Integer> queryVector = tokenFrequencies(query);
        if (queryVector.isEmpty()) {
            return List.of();
        }
        return indexedChunks.stream()
                .map(indexedChunk -> new RagMatch(indexedChunk.chunk(), cosineSimilarity(queryVector, indexedChunk.termFrequencies())))
                .filter(match -> match.score() > 0.0d)
                .sorted(Comparator.comparingDouble(RagMatch::score).reversed())
                .limit(topK)
                .toList();
    }

    private static Map<String, Integer> tokenFrequencies(String text) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        for (String token : tokenize(text)) {
            frequencies.merge(token, 1, Integer::sum);
        }
        return frequencies;
    }

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

    private static double cosineSimilarity(Map<String, Integer> left, Map<String, Integer> right) {
        double dotProduct = 0.0d;
        for (Map.Entry<String, Integer> entry : left.entrySet()) {
            dotProduct += entry.getValue() * right.getOrDefault(entry.getKey(), 0);
        }
        if (dotProduct == 0.0d) {
            return 0.0d;
        }
        return dotProduct / (vectorMagnitude(left.values()) * vectorMagnitude(right.values()));
    }

    private static double vectorMagnitude(Collection<Integer> values) {
        double sum = 0.0d;
        for (Integer value : values) {
            sum += value * value;
        }
        return Math.sqrt(sum);
    }

    private record IndexedChunk(
            RagChunk chunk,
            Map<String, Integer> termFrequencies
    ) {
    }
}
