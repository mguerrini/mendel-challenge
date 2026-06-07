package com.mendel.transactions.dto;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public class TypesResponse {

    private final List<Long> transactionIds;

    public TypesResponse(List<Long> transactionIds) {
        this.transactionIds = transactionIds;
    }

    @JsonValue
    public List<Long> getTransactionIds() {
        return transactionIds;
    }
}
