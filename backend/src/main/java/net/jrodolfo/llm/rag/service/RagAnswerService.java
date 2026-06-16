package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.dto.RagTimingMetadata;
import net.jrodolfo.llm.model.ChatRagSourceChunk;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.rag.dto.RagComparisonResponse;
import net.jrodolfo.llm.rag.dto.RagComparisonTargetResponse;
import net.jrodolfo.llm.rag.dto.RagQueryResponse;
import net.jrodolfo.llm.rag.dto.RagSourceChunkResponse;
import net.jrodolfo.llm.rag.config.RagProperties;
import net.jrodolfo.llm.rag.config.RagRetrievalTarget;
import net.jrodolfo.llm.rag.model.RagMatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

/**
 * Service responsible for generating answers using the RAG (Retrieval-Augmented Generation) pattern.
 * It retrieves relevant documents, builds a prompt, and uses an LLM to generate the final response.
 */
@Service
public class RagAnswerService {

    private final ChatModelProviderRegistry providerRegistry;
    private final RagRetrievalService ragRetrievalService;
    private final RagSessionService ragSessionService;
    private final RagProperties ragProperties;

    /**
     * Constructs a new RagAnswerService.
     *
     * @param providerRegistry    the registry of available chat model providers
     * @param ragRetrievalService the service used for document retrieval
     * @param ragSessionService   the service used for session management
     */
    @Autowired
    public RagAnswerService(
            ChatModelProviderRegistry providerRegistry,
            RagRetrievalService ragRetrievalService,
            RagSessionService ragSessionService,
            RagProperties ragProperties
    ) {
        this.providerRegistry = providerRegistry;
        this.ragRetrievalService = ragRetrievalService;
        this.ragSessionService = ragSessionService;
        this.ragProperties = ragProperties;
    }

    public RagAnswerService(
            ChatModelProviderRegistry providerRegistry,
            RagRetrievalService ragRetrievalService,
            RagSessionService ragSessionService
    ) {
        this(
                providerRegistry,
                ragRetrievalService,
                ragSessionService,
                new RagProperties(true, "docs", 900, 160, 4, "lexical", "ollama", "nomic-embed-text")
        );
    }

    /**
     * Generates an answer for the given question using the specified model and provider.
     *
     * @param question  the user's question
     * @param provider  the LLM provider to use (e.g., "openai", "bedrock")
     * @param model     the specific model to use
     * @param sessionId the unique identifier for the chat session
     * @return a {@link RagQueryResponse} containing the answer and source metadata
     * @throws IllegalStateException if no relevant source chunks are found
     */
    public RagQueryResponse answer(String question, String provider, String model, String sessionId) {
        return answer(question, provider, model, sessionId, null);
    }

    /**
     * Generates an answer using an optional request-level retrieval target.
     *
     * @param question        the user's question
     * @param provider        the LLM provider to use
     * @param model           the requested model ID
     * @param sessionId       the session identifier, or null to create a new session
     * @param retrievalTarget optional retrieval target such as {@code lexical} or {@code vector:qdrant}
     * @return the generated answer, cited chunks, session ID, and retrieval metadata
     */
    public RagQueryResponse answer(String question, String provider, String model, String sessionId, String retrievalTarget) {
        RagRetrievalTarget target = RagRetrievalTarget.fromRequestOrDefault(retrievalTarget, ragProperties);
        long requestStartedAt = System.nanoTime();
        long retrievalStartedAt = System.nanoTime();
        List<RagMatch> matches = ragRetrievalService.retrieve(question, target);
        long retrievalDurationMs = elapsedMillis(retrievalStartedAt);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No relevant source chunks were found in the RAG corpus.");
        }

        ChatModelProvider chatModelProvider = providerRegistry.get(provider);
        String resolvedProvider = providerRegistry.resolveProviderName(provider);
        String resolvedModel = chatModelProvider.resolveModel(model);
        ChatSession session = ragSessionService.startTurn(sessionId, resolvedModel, question);
        long providerStartedAt = System.nanoTime();
        ChatResponse response = chatModelProvider.chat(
                ProviderPrompt.forPrompt(buildPrompt(question, matches)),
                resolvedModel,
                null,
                null,
                session.sessionId(),
                null
        );
        long providerDurationMs = elapsedMillis(providerStartedAt);

        List<RagSourceChunkResponse> sources = matches.stream()
                .map(match -> new RagSourceChunkResponse(
                        match.chunk().sourcePath(),
                        match.chunk().title(),
                        match.chunk().text(),
                        roundScore(match.score())
                ))
                .toList();
        List<ChatRagSourceChunk> persistedSources = sources.stream()
                .map(source -> new ChatRagSourceChunk(source.sourcePath(), source.title(), source.excerpt(), source.score()))
                .toList();
        RagRetrievalMetadata ragRetrieval = ragRetrievalService.activeMetadata(target);
        RagTimingMetadata ragTiming = new RagTimingMetadata(
                retrievalDurationMs,
                providerDurationMs,
                elapsedMillis(requestStartedAt)
        );
        ChatSession persistedSession = ragSessionService.finishTurn(
                session,
                response.response(),
                response.metadata(),
                persistedSources,
                ragRetrieval,
                ragTiming
        );

        return new RagQueryResponse(
                response.response(),
                resolvedProvider,
                response.model(),
                persistedSession.sessionId(),
                sources,
                response.metadata(),
                ragRetrieval,
                ragTiming
        );
    }

    /**
     * Compares one question across multiple retrieval targets without persisting
     * the generated answers to a RAG session.
     *
     * @param question the user's question
     * @param provider the LLM provider to use
     * @param model the requested model ID
     * @param retrievalTargets optional target values; defaults to all supported targets
     * @return comparison response containing one result per target
     */
    public RagComparisonResponse compare(String question, String provider, String model, List<String> retrievalTargets) {
        List<RagRetrievalTarget> targets = resolveComparisonTargets(retrievalTargets);
        List<RagComparisonTargetResponse> results = targets.stream()
                .map(target -> compareTarget(question, provider, model, target))
                .toList();
        return new RagComparisonResponse(question, results);
    }

    private List<RagRetrievalTarget> resolveComparisonTargets(List<String> retrievalTargets) {
        if (retrievalTargets == null || retrievalTargets.isEmpty()) {
            return Arrays.asList(RagRetrievalTarget.values());
        }
        return retrievalTargets.stream()
                .map(RagRetrievalTarget::fromValue)
                .toList();
    }

    private RagComparisonTargetResponse compareTarget(
            String question,
            String provider,
            String model,
            RagRetrievalTarget target
    ) {
        try {
            return successfulComparisonTarget(question, provider, model, target);
        } catch (RuntimeException ex) {
            return failedComparisonTarget(target, ex);
        }
    }

    private RagComparisonTargetResponse successfulComparisonTarget(
            String question,
            String provider,
            String model,
            RagRetrievalTarget target
    ) {
        long requestStartedAt = System.nanoTime();
        long retrievalStartedAt = System.nanoTime();
        List<RagMatch> matches = ragRetrievalService.retrieve(question, target);
        long retrievalDurationMs = elapsedMillis(retrievalStartedAt);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No relevant source chunks were found in the RAG corpus.");
        }

        ChatModelProvider chatModelProvider = providerRegistry.get(provider);
        String resolvedProvider = providerRegistry.resolveProviderName(provider);
        String resolvedModel = chatModelProvider.resolveModel(model);
        long providerStartedAt = System.nanoTime();
        ChatResponse response = chatModelProvider.chat(
                ProviderPrompt.forPrompt(buildPrompt(question, matches)),
                resolvedModel,
                null,
                null,
                null,
                null
        );
        long providerDurationMs = elapsedMillis(providerStartedAt);

        RagRetrievalMetadata ragRetrieval = ragRetrievalService.activeMetadata(target);
        RagTimingMetadata ragTiming = new RagTimingMetadata(
                retrievalDurationMs,
                providerDurationMs,
                elapsedMillis(requestStartedAt)
        );

        return new RagComparisonTargetResponse(
                target.value(),
                true,
                null,
                response.response(),
                resolvedProvider,
                response.model(),
                toSourceResponses(matches),
                response.metadata(),
                ragRetrieval,
                ragTiming
        );
    }

    private RagComparisonTargetResponse failedComparisonTarget(RagRetrievalTarget target, RuntimeException ex) {
        return new RagComparisonTargetResponse(
                target.value(),
                false,
                ex.getMessage(),
                null,
                null,
                null,
                List.of(),
                null,
                ragRetrievalService.activeMetadata(target),
                null
        );
    }

    /**
     * Builds a prompt for the LLM using the provided question and retrieved matches.
     *
     * @param question The user's question.
     * @param matches  The relevant document matches.
     * @return A formatted prompt string.
     */
    private String buildPrompt(String question, List<RagMatch> matches) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are answering questions about this project using only the provided documentation excerpts.
                If the excerpts do not contain enough information, say so plainly.
                Cite the provided source paths naturally in the answer when relevant.
                Do not add generic caveats that contradict the excerpts.
                Do not say there is no specific mention of something if the excerpts contain concrete commands, checks, or configuration values for it.
                For troubleshooting questions, answer as a concise checklist using only the checks present in the excerpts.
                
                Question:
                """);
        prompt.append(question).append("\n\n");
        prompt.append("Source excerpts:\n");
        for (int index = 0; index < matches.size(); index++) {
            RagMatch match = matches.get(index);
            prompt.append('[').append(index + 1).append("] ")
                    .append(match.chunk().sourcePath())
                    .append(" - ")
                    .append(match.chunk().title())
                    .append('\n')
                    .append(match.chunk().text())
                    .append("\n\n");
        }
        prompt.append("Answer the question using the excerpts above.");
        return prompt.toString();
    }

    private List<RagSourceChunkResponse> toSourceResponses(List<RagMatch> matches) {
        return matches.stream()
                .map(match -> new RagSourceChunkResponse(
                        match.chunk().sourcePath(),
                        match.chunk().title(),
                        match.chunk().text(),
                        roundScore(match.score())
                ))
                .toList();
    }

    /**
     * Rounds a score to three decimal places.
     *
     * @param value The raw score.
     * @return The rounded score.
     */
    private double roundScore(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    /**
     * Calculates elapsed milliseconds from a monotonic clock value.
     *
     * @param startedAtNanos start time from {@link System#nanoTime()}
     * @return elapsed milliseconds, never negative
     */
    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (System.nanoTime() - startedAtNanos) / 1_000_000L);
    }
}
