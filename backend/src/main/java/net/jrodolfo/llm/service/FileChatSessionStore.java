package net.jrodolfo.llm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.jrodolfo.llm.config.AppStorageProperties;
import net.jrodolfo.llm.model.ChatSession;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class FileChatSessionStore {

    private final ObjectMapper objectMapper;
    private final Path sessionsDirectory;
    private final SessionIdPolicy sessionIdPolicy;

    public FileChatSessionStore(ObjectMapper objectMapper, AppStorageProperties properties, SessionIdPolicy sessionIdPolicy) {
        this.objectMapper = objectMapper;
        this.sessionsDirectory = properties.resolvedSessionsDirectory();
        this.sessionIdPolicy = sessionIdPolicy;
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

    public List<ChatSession> findAll() {
        if (!Files.exists(sessionsDirectory)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(sessionsDirectory)) {
            return paths
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .map(this::readSession)
                    .toList();
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to list chat sessions.", ex);
        }
    }

    public boolean deleteById(String sessionId) {
        Path sessionPath = resolveSessionPath(sessionId);
        try {
            return Files.deleteIfExists(sessionPath);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to delete chat session: " + sessionId, ex);
        }
    }

    private ChatSession readSession(Path sessionPath) {
        try {
            return objectMapper.readValue(sessionPath.toFile(), ChatSession.class);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to read chat session: " + sessionPath.getFileName(), ex);
        }
    }

    private Path resolveSessionPath(String sessionId) {
        String safeSessionId = sessionIdPolicy.requireValid(sessionId);
        Path normalizedRoot = sessionsDirectory.toAbsolutePath().normalize();
        Path candidate = normalizedRoot.resolve(safeSessionId + ".json").normalize();
        if (!candidate.startsWith(normalizedRoot)) {
            throw new InvalidSessionIdException("Invalid session id.");
        }
        return candidate;
    }
}
