package com.mendel.transactions.service;

import com.mendel.transactions.exception.InvalidTransactionException;
import com.mendel.transactions.exception.ParentNotFoundException;
import com.mendel.transactions.exception.TransactionAlreadyExistsException;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.model.Transaction;
import com.mendel.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;

    public TransactionServiceImpl(TransactionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void createTransaction(long transactionId, double amount, String type, Long parentId) {
        validate(transactionId, type);

        if (repository.existsById(transactionId)) {
            throw new TransactionAlreadyExistsException(transactionId);
        }

        String path;
        List<Long> ancestorIds;

        if (parentId != null) {
            Transaction parent = repository.findById(parentId)
                    .orElseThrow(() -> new ParentNotFoundException(parentId));
            path        = parent.getPath() + "/" + transactionId;
            ancestorIds = parsePath(parent.getPath());
        } else {
            path        = String.valueOf(transactionId);
            ancestorIds = Collections.emptyList();
        }

        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .amount(amount)
                .type(type)
                .parentId(parentId)
                .path(path)
                .accumulatedSum(amount)
                .propagated(ancestorIds.isEmpty())
                .build();

        repository.saveWithPropagation(transaction, ancestorIds, amount);
    }

    @Override
    public List<Long> getTransactionIdsByType(String type) {
        return repository.findIdsByType(type);
    }

    @Override
    public double getAccumulatedSum(long transactionId) {
        return repository.findById(transactionId)
                .orElseThrow(() -> new TransactionNotFoundException(transactionId))
                .getAccumulatedSum();
    }

    private void validate(long transactionId, String type) {
        if (transactionId <= 0) {
            throw new InvalidTransactionException("Transaction id must be a positive number");
        }
        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException("Type must not be empty");
        }
    }

    private List<Long> parsePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(Long::parseLong)
                .collect(Collectors.toList());
    }
}
