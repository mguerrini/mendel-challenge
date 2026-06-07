package com.mendel.transactions.exception;

public class VersionConflictException extends RuntimeException {

    public VersionConflictException(long transactionId, long expected, long actual) {
        super(String.format("Version conflict on transaction %d: expected %d, got %d",
                transactionId, expected, actual));
    }
}
