package net.jrodolfo.llm.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class SessionIdPolicy {

    private static final Pattern ALLOWED_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    public String requireValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new InvalidSessionIdException("Session id must not be blank.");
        }

        String normalized = sessionId.trim();
        if (!ALLOWED_SESSION_ID.matcher(normalized).matches()) {
            throw new InvalidSessionIdException("Invalid session id.");
        }

        return normalized;
    }

    public String requireValidOrGenerate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requireValid(sessionId);
    }

    public boolean isValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return ALLOWED_SESSION_ID.matcher(sessionId.trim()).matches();
    }
}
