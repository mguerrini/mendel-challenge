package com.mendel.transactions.service;

import com.mendel.transactions.dto.TransactionRequest;
import com.mendel.transactions.exception.InvalidTransactionException;
import com.mendel.transactions.exception.ParentNotFoundException;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.model.Transaction;

import com.mendel.transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository repository;
    private final TransactionPropagatorService propagatorService;

    public TransactionServiceImpl(TransactionRepository repository, TransactionPropagatorService propagatorService) {
        this.repository = repository;
        this.propagatorService = propagatorService;
    }

    @Override
    public void createTransaction(TransactionRequest request) {
        long transactionId = request.getTransactionId();
        validate(transactionId, request.getType());

        boolean hasParent = request.getParentId() != null;
        String path = buildPath(transactionId, request.getParentId());

        Transaction transaction = Transaction.builder()
                .id(transactionId)
                .amount(request.getAmount())
                .type(request.getType())
                .parentId(request.getParentId())
                .path(path)
                .accumulatedSum(request.getAmount())
                .propagated(!hasParent)
                .build();

        repository.save(transaction);

        if (hasParent) {
            propagatorService.propagate(transaction);
        }
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

    private String buildPath(long transactionId, Long parentId) {
        if (parentId == null) {
            return String.valueOf(transactionId);
        }
        Transaction parent = repository.findById(parentId)
                .orElseThrow(() -> new ParentNotFoundException(parentId));
        return parent.getPath() + "/" + transactionId;
    }

    private void validate(long transactionId, String type) {
        if (transactionId <= 0) {
            throw new InvalidTransactionException("Transaction id must be a positive number");
        }
        if (type == null || type.isBlank()) {
            throw new InvalidTransactionException("Type must not be empty");
        }
    }
}
