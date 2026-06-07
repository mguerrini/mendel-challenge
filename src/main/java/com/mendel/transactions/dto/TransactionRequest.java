package com.mendel.transactions.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TransactionRequest {

    private final double amount;
    private final String type;
    private final Long parentId;

    public TransactionRequest(
            @JsonProperty("amount") double amount,
            @JsonProperty("type") String type,
            @JsonProperty("parent_id") Long parentId) {
        this.amount   = amount;
        this.type     = type;
        this.parentId = parentId;
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
