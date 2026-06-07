package com.mendel.transactions.dto;

import java.time.Instant;

public class ErrorResponse {

    private final String error;
    private final String message;
    private final Instant timestamp;

    public ErrorResponse(String error, String message) {
        this.error     = error;
        this.message   = message;
        this.timestamp = Instant.now();
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
