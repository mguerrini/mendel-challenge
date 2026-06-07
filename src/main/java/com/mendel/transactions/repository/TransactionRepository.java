package com.mendel.transactions.repository;

import com.mendel.transactions.model.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    Optional<Transaction> findById(long id);

    List<Long> findIdsByType(String type);

    boolean existsById(long id);

    void saveWithPropagation(Transaction transaction, List<Long> ancestorIds, double amount);
}
