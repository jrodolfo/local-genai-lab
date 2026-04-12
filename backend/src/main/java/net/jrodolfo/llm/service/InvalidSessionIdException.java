package net.jrodolfo.llm.service;

public class InvalidSessionIdException extends RuntimeException {

    public InvalidSessionIdException(String message) {
        super(message);
    }
}
