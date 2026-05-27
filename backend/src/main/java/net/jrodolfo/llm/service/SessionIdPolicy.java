package net.jrodolfo.llm.service;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Policy for validating and generating chat session IDs.
 */
@Component
public class SessionIdPolicy {

    private static final Pattern ALLOWED_SESSION_ID = Pattern.compile("^[A-Za-z0-9_-]{1,100}$");

    /**
     * Validates a session ID and returns it if valid, otherwise throws an exception.
     *
     * @param sessionId the session ID to validate
     * @return the validated session ID
     * @throws InvalidSessionIdException if the session ID is invalid
     */
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

    /**
     * Validates a session ID or generates a new one if it is blank.
     *
     * @param sessionId the session ID to validate or null/blank
     * @return a valid session ID
     */
    public String requireValidOrGenerate(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requireValid(sessionId);
    }

    /**
     * Checks if a session ID is valid according to the policy.
     *
     * @param sessionId the session ID to check
     * @return true if valid, false otherwise
     */
    public boolean isValid(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        return ALLOWED_SESSION_ID.matcher(sessionId.trim()).matches();
    }
}
