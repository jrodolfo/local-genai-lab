package net.jrodolfo.llm.rag.service;

import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.RagRetrievalMetadata;
import net.jrodolfo.llm.model.ChatRagSourceChunk;
import net.jrodolfo.llm.model.ChatSession;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.rag.dto.RagQueryResponse;
import net.jrodolfo.llm.rag.dto.RagSourceChunkResponse;
import net.jrodolfo.llm.rag.model.RagMatch;
import org.springframework.stereotype.Service;

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

    /**
     * Constructs a new RagAnswerService.
     *
     * @param providerRegistry    the registry of available chat model providers
     * @param ragRetrievalService the service used for document retrieval
     * @param ragSessionService   the service used for session management
     */
    public RagAnswerService(
            ChatModelProviderRegistry providerRegistry,
            RagRetrievalService ragRetrievalService,
            RagSessionService ragSessionService
    ) {
        this.providerRegistry = providerRegistry;
        this.ragRetrievalService = ragRetrievalService;
        this.ragSessionService = ragSessionService;
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

    public RagQueryResponse answer(
            String question,
            String provider,
            String model,
            String sessionId,
            RagRetrievalOptions retrievalOptions
    ) {
        return answer(question, provider, model, sessionId, retrievalOptions, true);
    }

    public RagQueryResponse answer(
            String question,
            String provider,
            String model,
            String sessionId,
            RagRetrievalOptions retrievalOptions,
            boolean persist
    ) {
        List<RagMatch> matches = retrievalOptions == null
                ? ragRetrievalService.retrieve(question)
                : ragRetrievalService.retrieve(question, retrievalOptions);
        if (matches.isEmpty()) {
            throw new IllegalStateException("No relevant source chunks were found in the RAG corpus.");
        }

        ChatModelProvider chatModelProvider = providerRegistry.get(provider);
        String resolvedProvider = providerRegistry.resolveProviderName(provider);
        String resolvedModel = chatModelProvider.resolveModel(model);
        ChatSession session = persist ? ragSessionService.startTurn(sessionId, resolvedModel, question) : null;
        ChatResponse response = chatModelProvider.chat(
                ProviderPrompt.forPrompt(buildPrompt(question, matches)),
                resolvedModel,
                null,
                null,
                session != null ? session.sessionId() : sessionId,
                null
        );

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
        RagRetrievalMetadata ragRetrieval = retrievalMetadata(retrievalOptions);
        String responseSessionId = sessionId;
        if (persist) {
            ChatSession persistedSession = ragSessionService.finishTurn(
                    session,
                    response.response(),
                    response.metadata(),
                    persistedSources,
                    ragRetrieval
            );
            responseSessionId = persistedSession.sessionId();
        }

        return new RagQueryResponse(
                response.response(),
                resolvedProvider,
                response.model(),
                responseSessionId,
                sources,
                response.metadata(),
                ragRetrieval
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

    /**
     * Rounds a score to three decimal places.
     *
     * @param value The raw score.
     * @return The rounded score.
     */
    private double roundScore(double value) {
        return Math.round(value * 1000.0d) / 1000.0d;
    }

    private RagRetrievalMetadata retrievalMetadata(RagRetrievalOptions retrievalOptions) {
        if (retrievalOptions == null) {
            return null;
        }
        return new RagRetrievalMetadata(
                retrievalOptions.retrievalMode().configValue(),
                retrievalOptions.vectorStore().configValue(),
                retrievalOptions.retrievalMode().configValue() + ":" + retrievalOptions.vectorStore().configValue()
        );
    }
}
