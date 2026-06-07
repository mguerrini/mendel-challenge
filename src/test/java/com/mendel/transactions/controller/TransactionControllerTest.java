package com.mendel.transactions.controller;

import com.mendel.transactions.exception.InvalidTransactionException;
import com.mendel.transactions.exception.ParentNotFoundException;
import com.mendel.transactions.exception.TransactionAlreadyExistsException;
import com.mendel.transactions.exception.TransactionNotFoundException;
import com.mendel.transactions.service.TransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TransactionService transactionService;

    // --- PUT /transactions/{id} ---

    @Test
    void createTransaction_returnsCreatedWithStatusOk() throws Exception {
        mockMvc.perform(put("/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000.0, "type": "cars" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void createTransaction_passesRequestWithTransactionIdToService() throws Exception {
        mockMvc.perform(put("/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000.0, "type": "cars" }
                                """))
                .andExpect(status().isCreated());

        verify(transactionService).createTransaction(
                argThat(req -> req.getTransactionId() == 10L
                        && req.getAmount() == 5000.0
                        && req.getType().equals("cars")
                        && req.getParentId() == null));
    }

    @Test
    void createTransaction_withParentId_passesParentIdToService() throws Exception {
        mockMvc.perform(put("/transactions/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 10000.0, "type": "shopping", "parent_id": 10 }
                                """))
                .andExpect(status().isCreated());

        verify(transactionService).createTransaction(
                argThat(req -> req.getTransactionId() == 11L
                        && req.getParentId() == 10L));
    }

    @Test
    void createTransaction_duplicateId_returns409() throws Exception {
        doThrow(new TransactionAlreadyExistsException(10L))
                .when(transactionService).createTransaction(any());

        mockMvc.perform(put("/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000.0, "type": "cars" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("TRANSACTION_ALREADY_EXISTS"));
    }

    @Test
    void createTransaction_parentNotFound_returns404() throws Exception {
        doThrow(new ParentNotFoundException(99L))
                .when(transactionService).createTransaction(any());

        mockMvc.perform(put("/transactions/11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 1000.0, "type": "shopping", "parent_id": 99 }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PARENT_NOT_FOUND"));
    }

    @Test
    void createTransaction_invalidRequest_returns400() throws Exception {
        doThrow(new InvalidTransactionException("Type must not be empty"))
                .when(transactionService).createTransaction(any());

        mockMvc.perform(put("/transactions/10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "amount": 5000.0, "type": "" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_TRANSACTION"));
    }

    // --- GET /transactions/types/{type} ---

    @Test
    void getTransactionsByType_returnsListOfIds() throws Exception {
        when(transactionService.getTransactionIdsByType("cars")).thenReturn(List.of(10L, 11L));

        mockMvc.perform(get("/transactions/types/cars"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value(10))
                .andExpect(jsonPath("$[1]").value(11));
    }

    @Test
    void getTransactionsByType_noResults_returnsEmptyList() throws Exception {
        when(transactionService.getTransactionIdsByType("unknown")).thenReturn(List.of());

        mockMvc.perform(get("/transactions/types/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // --- GET /transactions/sum/{id} ---

    @Test
    void getAccumulatedSum_returnsSum() throws Exception {
        when(transactionService.getAccumulatedSum(10L)).thenReturn(20000.0);

        mockMvc.perform(get("/transactions/sum/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sum").value(20000.0));
    }

    @Test
    void getAccumulatedSum_transactionNotFound_returns404() throws Exception {
        when(transactionService.getAccumulatedSum(99L))
                .thenThrow(new TransactionNotFoundException(99L));

        mockMvc.perform(get("/transactions/sum/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("TRANSACTION_NOT_FOUND"));
    }
}
