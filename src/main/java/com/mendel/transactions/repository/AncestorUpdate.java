package com.mendel.transactions.repository;

public record AncestorUpdate(long transactionId, double newAccumulatedSum, long expectedVersion) {
}
