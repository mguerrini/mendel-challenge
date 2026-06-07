package com.mendel.transactions.service;

import com.mendel.transactions.exception.AlreadyPropagatedException;
import com.mendel.transactions.exception.VersionConflictException;
import com.mendel.transactions.model.Transaction;
import com.mendel.transactions.repository.AncestorUpdate;
import com.mendel.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionPropagatorService {

    private static final int MAX_RETRIES = 3;

    private final TransactionRepository repository;

    public TransactionPropagatorService(TransactionRepository repository) {
        this.repository = repository;
    }

    public void propagate(Transaction transaction) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            List<Transaction> ancestors = repository.findAncestors(transaction.getPath());
            List<AncestorUpdate> updates = buildUpdates(ancestors, transaction.getAmount());
            try {
                repository.applyPropagation(transaction, updates);
                return;
            } catch (AlreadyPropagatedException e) {
                return;
            } catch (VersionConflictException e) {
                if (attempt == MAX_RETRIES - 1) {
                    throw e;
                }
            }
        }
    }

    private List<AncestorUpdate> buildUpdates(List<Transaction> ancestors, double amount) {
        return ancestors.stream()
                .map(a -> new AncestorUpdate(
                        a.getId(),
                        a.getAccumulatedSum() + amount,
                        a.getVersion()))
                .collect(Collectors.toList());
    }
}
