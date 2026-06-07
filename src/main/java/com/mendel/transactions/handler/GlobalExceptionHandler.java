package com.mendel.transactions.handler;

import com.mendel.transactions.dto.ErrorResponse;
import com.mendel.transactions.exception.InvalidTransactionException;
import com.mendel.transactions.exception.ParentNotFoundException;
import com.mendel.transactions.exception.TransactionAlreadyExistsException;
import com.mendel.transactions.exception.TransactionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(TransactionAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse handleAlreadyExists(TransactionAlreadyExistsException ex) {
        return new ErrorResponse("TRANSACTION_ALREADY_EXISTS", ex.getMessage());
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(TransactionNotFoundException ex) {
        return new ErrorResponse("TRANSACTION_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(ParentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleParentNotFound(ParentNotFoundException ex) {
        return new ErrorResponse("PARENT_NOT_FOUND", ex.getMessage());
    }

    @ExceptionHandler(InvalidTransactionException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleInvalidTransaction(InvalidTransactionException ex) {
        return new ErrorResponse("INVALID_TRANSACTION", ex.getMessage());
    }
}
