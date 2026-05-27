package net.jrodolfo.llm.service;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an error occurs while accessing a chat artifact.
 */
public class ArtifactAccessException extends RuntimeException {

    private final HttpStatus status;

    /**
     * Constructs a new ArtifactAccessException.
     *
     * @param status the HTTP status code related to the error
     * @param message the detail message
     */
    public ArtifactAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Gets the HTTP status code related to the error.
     *
     * @return the HTTP status
     */
    public HttpStatus status() {
        return status;
    }
}
