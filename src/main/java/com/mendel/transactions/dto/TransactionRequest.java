package com.mendel.transactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionRequest {

    private final long transactionId;
    private final double amount;
    private final String type;
    private final Long parentId;

    public TransactionRequest(
            @JsonProperty("transaction_id") long transactionId,
            @JsonProperty("amount") double amount,
            @JsonProperty("type") String type,
            @JsonProperty("parent_id") Long parentId) {
        this.transactionId = transactionId;
        this.amount        = amount;
        this.type          = type;
        this.parentId      = parentId;
    }

    public TransactionRequest withTransactionId(long transactionId) {
        return new TransactionRequest(transactionId, amount, type, parentId);
    }

    public long getTransactionId() {
        return transactionId;
    }

    public double getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public Long getParentId() {
        return parentId;
    }
}
