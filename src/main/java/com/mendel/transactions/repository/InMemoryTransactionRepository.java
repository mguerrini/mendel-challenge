package com.mendel.transactions.repository;

import com.mendel.transactions.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final ConcurrentHashMap<Long, Transaction> store = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    @Override
    public Optional<Transaction> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Long> findIdsByType(String type) {
        return store.values().stream()
                .filter(tx -> tx.getType().equals(type))
                .map(Transaction::getId)
                .collect(Collectors.toList());
    }

    @Override
    public boolean existsById(long id) {
        return store.containsKey(id);
    }

    @Override
    public void saveWithPropagation(Transaction transaction, List<Long> ancestorIds, double amount) {
        writeLock.lock();
        try {
            store.put(transaction.getId(), transaction);
            for (long ancestorId : ancestorIds) {
                Transaction ancestor = store.get(ancestorId);
                if (ancestor != null) {
                    ancestor.addToAccumulatedSum(amount);
                }
            }
            transaction.markAsPropagated();
        } finally {
            writeLock.unlock();
        }
    }
}
