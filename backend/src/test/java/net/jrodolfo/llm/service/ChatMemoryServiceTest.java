package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.dto.ChatToolMetadata;
import net.jrodolfo.llm.model.ChatSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatMemoryServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void startAndFinishTurnPersistSessionMessages() {
        ChatMemoryService memoryService = new ChatMemoryService(newSessionStore());

        ChatSession started = memoryService.startTurn(null, "llama3:8b", "llama3:8b", "hello");
        ChatSession finished = memoryService.finishTurn(
                started,
                "hi there",
                new ChatToolMetadata(true, "list_recent_reports", "success", "Found 1 report.")
        );

        assertNotNull(finished.sessionId());
        assertEquals(2, finished.messages().size());
        assertEquals("user", finished.messages().get(0).role());
        assertEquals("assistant", finished.messages().get(1).role());
        assertEquals("list_recent_reports", finished.messages().get(1).tool().name());
        assertTrue(tempDir.resolve("sessions").resolve(finished.sessionId() + ".json").toFile().exists());
    }

    @Test
    void historyBeforeLatestUserMessageExcludesCurrentPrompt() {
        ChatMemoryService memoryService = new ChatMemoryService(newSessionStore());

        ChatSession firstTurn = memoryService.finishTurn(
                memoryService.startTurn("session-1", "llama3:8b", "llama3:8b", "first"),
                "first answer",
                null
        );
        ChatSession secondTurn = memoryService.startTurn(firstTurn.sessionId(), "llama3:8b", "llama3:8b", "second");

        var history = memoryService.historyBeforeLatestUserMessage(secondTurn);

        assertEquals(2, history.size());
        assertEquals("first", history.get(0).content());
        assertEquals("first answer", history.get(1).content());
    }

    private FileChatSessionStore newSessionStore() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new FileChatSessionStore(objectMapper, new AppStorageProperties(tempDir.resolve("sessions").toString()));
    }
}
