package com.example.coaextractor.client;

public class S4HanaClientException extends RuntimeException {

    public S4HanaClientException(String message) {
        super(message);
    }

    public S4HanaClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
