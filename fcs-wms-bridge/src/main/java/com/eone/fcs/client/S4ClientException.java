package com.eone.fcs.client;

public class S4ClientException extends RuntimeException {
    public S4ClientException(String message) { super(message); }
    public S4ClientException(String message, Throwable cause) { super(message, cause); }
}
