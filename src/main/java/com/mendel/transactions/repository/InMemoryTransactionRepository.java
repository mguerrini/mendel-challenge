package com.mendel.transactions.repository;

import com.mendel.transactions.exception.AlreadyPropagatedException;
import com.mendel.transactions.exception.TransactionAlreadyExistsException;
import com.mendel.transactions.exception.VersionConflictException;
import com.mendel.transactions.model.Transaction;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Repository
public class InMemoryTransactionRepository implements TransactionRepository {

    private final ConcurrentHashMap<Long, Transaction> store = new ConcurrentHashMap<>();
    private final ReentrantLock writeLock = new ReentrantLock();

    @Override
    public void save(Transaction transaction) {
        if (store.putIfAbsent(transaction.getId(), transaction) != null) {
            throw new TransactionAlreadyExistsException(transaction.getId());
        }
    }

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
    public List<Transaction> findAncestors(String path) {
        String[] segments = path.split("/");
        return Arrays.stream(segments, 0, segments.length - 1)
                .map(s -> store.get(Long.parseLong(s)))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public void applyPropagation(Transaction transaction, List<AncestorUpdate> updates) {
        writeLock.lock();
        try {
            if (transaction.isPropagated()) {
                throw new AlreadyPropagatedException(transaction.getId());
            }
            for (AncestorUpdate update : updates) {
                Transaction current = store.get(update.transactionId());
                if (current.getVersion() != update.expectedVersion()) {
                    throw new VersionConflictException(update.transactionId(), update.expectedVersion(), current.getVersion());
                }
            }
            for (AncestorUpdate update : updates) {
                store.get(update.transactionId()).updateAccumulatedSum(update.newAccumulatedSum());
            }
            transaction.markAsPropagated();
        } finally {
            writeLock.unlock();
        }
    }
}
