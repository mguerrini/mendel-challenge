package com.mendel.transactions.service;

import com.mendel.transactions.dto.TransactionRequest;

import java.util.List;

public interface TransactionService {

    void createTransaction(TransactionRequest request);

    List<Long> getTransactionIdsByType(String type);

    double getAccumulatedSum(long transactionId);
}
