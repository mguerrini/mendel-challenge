package com.mendel.transactions.exception;

public class AlreadyPropagatedException extends RuntimeException {

    public AlreadyPropagatedException(long transactionId) {
        super("Transaction " + transactionId + " was already propagated");
    }
}
