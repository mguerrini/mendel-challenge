package com.mendel.transactions.controller;

import com.mendel.transactions.dto.StatusResponse;
import com.mendel.transactions.dto.SumResponse;
import com.mendel.transactions.dto.TransactionRequest;
import com.mendel.transactions.service.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PutMapping("/{transactionId}")
    @ResponseStatus(HttpStatus.CREATED)
    public StatusResponse createTransaction(
            @PathVariable long transactionId,
            @RequestBody TransactionRequest request) {
        transactionService.createTransaction(
                transactionId,
                request.getAmount(),
                request.getType(),
                request.getParentId());
        return StatusResponse.ok();
    }

    @GetMapping("/types/{type}")
    public List<Long> getTransactionsByType(@PathVariable String type) {
        return transactionService.getTransactionIdsByType(type);
    }

    @GetMapping("/sum/{transactionId}")
    public SumResponse getAccumulatedSum(@PathVariable long transactionId) {
        return new SumResponse(transactionService.getAccumulatedSum(transactionId));
    }
}
