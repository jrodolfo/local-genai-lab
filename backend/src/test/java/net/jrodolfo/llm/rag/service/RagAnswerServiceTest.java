package net.jrodolfo.llm.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.config.AppModelProperties;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ChatResponse;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.dto.ModelProviderMetadata;
import net.jrodolfo.llm.dto.PendingToolCallResponse;
import net.jrodolfo.llm.provider.ChatModelProvider;
import net.jrodolfo.llm.provider.ChatModelProviderRegistry;
import net.jrodolfo.llm.provider.ProviderPrompt;
import net.jrodolfo.llm.provider.StreamingChatResult;
import net.jrodolfo.llm.rag.model.RagChunk;
import net.jrodolfo.llm.rag.model.RagMatch;
import net.jrodolfo.llm.service.ChatSessionMetadataService;
import net.jrodolfo.llm.service.FileChatSessionStore;
import net.jrodolfo.llm.service.SessionIdPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RagAnswerServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void promptPreventsContradictoryNoSpecificMentionCaveats() {
        CapturingProvider provider = new CapturingProvider();
        RagAnswerService answerService = new RagAnswerService(
                new ChatModelProviderRegistry(new AppModelProperties("ollama"), Map.of("ollama", provider)),
                new StubRetrievalService(List.of(new RagMatch(
                        new RagChunk(
                                "rag-troubleshooting.md#0",
                                "rag-troubleshooting.md",
                                "RAG Troubleshooting",
                                "When vector RAG is not working, run ./status.sh, confirm rag enabled: true, confirm rag retrieval mode: vector, confirm ollama service: ok, confirm ollama embedding model: present (nomic-embed-text), and click Rebuild Index after changing retrieval mode."
                        ),
                        0.95
                ))),
                new RagSessionService(
                        new FileChatSessionStore(
                                new ObjectMapper().registerModule(new JavaTimeModule()),
                                new AppStorageProperties(tempDir.resolve("sessions").toString(), tempDir.resolve("reports").toString()),
                                new SessionIdPolicy()
                        ),
                        new ChatSessionMetadataService(),
                        new SessionIdPolicy()
                )
        );

        answerService.answer("What should I check when vector RAG is not working?", "ollama", "llama3:8b", null);

        assertTrue(provider.prompt.prompt().contains("Do not add generic caveats that contradict the excerpts."));
        assertTrue(provider.prompt.prompt().contains("Do not say there is no specific mention of something"));
        assertTrue(provider.prompt.prompt().contains("For troubleshooting questions, answer as a concise checklist"));
        assertTrue(provider.prompt.prompt().contains("confirm rag enabled: true"));
        assertTrue(provider.prompt.prompt().contains("confirm ollama embedding model: present"));
    }

    private static final class StubRetrievalService extends RagRetrievalService {
        private final List<RagMatch> matches;

        private StubRetrievalService(List<RagMatch> matches) {
            super(null, null, null, null);
            this.matches = matches;
        }

        @Override
        public List<RagMatch> retrieve(String question) {
            return matches;
        }
    }

    private static final class CapturingProvider implements ChatModelProvider {
        private ProviderPrompt prompt;

        @Override
        public ChatResponse chat(
                ProviderPrompt prompt,
                String model,
                ChatToolMetadata toolMetadata,
                Map<String, Object> toolResult,
                String sessionId,
                PendingToolCallResponse pendingTool
        ) {
            this.prompt = prompt;
            return new ChatResponse(
                    "Check status, Ollama, the embedding model, and rebuild the index.",
                    model,
                    null,
                    null,
                    sessionId,
                    null,
                    new ModelProviderMetadata("ollama", model, null, null, null, null, null, null, null, null)
            );
        }

        @Override
        public StreamingChatResult streamChat(ProviderPrompt prompt, String model, Consumer<String> tokenConsumer) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String resolveModel(String model) {
            return model == null || model.isBlank() ? "llama3:8b" : model;
        }
    }
}
