package com.example.aics.common;

public class DocumentIngestException extends RuntimeException {

    public DocumentIngestException(String message) {
        super(message);
    }

    public DocumentIngestException(String message, Throwable cause) {
        super(message, cause);
    }
}
