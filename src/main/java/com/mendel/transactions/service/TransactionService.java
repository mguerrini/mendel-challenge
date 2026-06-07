package com.mendel.transactions.service;

import com.mendel.transactions.dto.StatusResponse;
import com.mendel.transactions.dto.SumResponse;
import com.mendel.transactions.dto.TransactionRequest;
import com.mendel.transactions.dto.TypesResponse;

public interface TransactionService {

    StatusResponse createTransaction(long transactionId, TransactionRequest request);

    TypesResponse getTransactionIdsByType(String type);

    SumResponse getAccumulatedSum(long transactionId);
}
