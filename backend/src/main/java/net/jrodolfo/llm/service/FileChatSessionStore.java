package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.model.ChatSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Service
public class FileChatSessionStore {

    private final ObjectMapper objectMapper;
    private final Path sessionsDirectory;

    public FileChatSessionStore(ObjectMapper objectMapper, AppStorageProperties properties) {
        this.objectMapper = objectMapper;
        this.sessionsDirectory = Path.of(properties.sessionsDirectory()).normalize();
    }

    public Optional<ChatSession> findById(String sessionId) {
        Path sessionPath = resolveSessionPath(sessionId);
        if (!Files.exists(sessionPath)) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(sessionPath.toFile(), ChatSession.class));
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read chat session: " + sessionId, ex);
        }
    }

    public ChatSession save(ChatSession session) {
        try {
            Files.createDirectories(sessionsDirectory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(resolveSessionPath(session.sessionId()).toFile(), session);
            return session;
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to save chat session: " + session.sessionId(), ex);
        }
    }

    private Path resolveSessionPath(String sessionId) {
        return sessionsDirectory.resolve(sessionId + ".json");
    }
}
