package com.mendel.transactions.exception;

public class TransactionNotFoundException extends RuntimeException {

    private final long transactionId;

    public TransactionNotFoundException(long transactionId) {
        super("Transaction with id " + transactionId + " not found");
        this.transactionId = transactionId;
    }

    public long getTransactionId() {
        return transactionId;
    }
}
