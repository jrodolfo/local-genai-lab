package net.jrodolfo.llm.service;

import org.springframework.http.HttpStatus;

public class ArtifactAccessException extends RuntimeException {

    private final HttpStatus status;

    public ArtifactAccessException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
