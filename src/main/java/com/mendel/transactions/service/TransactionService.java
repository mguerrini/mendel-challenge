package com.mendel.transactions.service;

import java.util.List;

public interface TransactionService {

    void createTransaction(long transactionId, double amount, String type, Long parentId);

    List<Long> getTransactionIdsByType(String type);

    double getAccumulatedSum(long transactionId);
}
