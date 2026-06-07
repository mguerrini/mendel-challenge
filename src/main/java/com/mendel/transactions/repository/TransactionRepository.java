package com.mendel.transactions.repository;

import com.mendel.transactions.model.Transaction;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository {

    void save(Transaction transaction);

    Optional<Transaction> findById(long id);

    List<Long> findIdsByType(String type);

    List<Transaction> findAncestors(String path);

    // Atomic equivalent of DynamoDB TransactWrite:
    // - guards on propagated == false (idempotence)
    // - validates version on each ancestor
    // - updates accumulatedSum on all ancestors
    // - marks transaction as propagated
    // Throws VersionConflictException if any ancestor version has changed.
    // Throws AlreadyPropagatedException if transaction was already propagated.
    void applyPropagation(Transaction transaction, List<AncestorUpdate> updates);
}
