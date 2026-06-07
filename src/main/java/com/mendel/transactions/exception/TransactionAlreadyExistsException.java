package com.mendel.transactions.exception;

public class TransactionAlreadyExistsException extends RuntimeException {

    private final long transactionId;

    public TransactionAlreadyExistsException(long transactionId) {
        super("Transaction with id " + transactionId + " already exists");
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }
}
