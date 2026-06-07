package com.mendel.transactions.service;

import com.mendel.transactions.dto.TransactionRequest;
import com.mendel.transactions.exception.InvalidTransactionException;
import com.mendel.transactions.exception.ParentNotFoundException;
import com.mendel.transactions.exception.TransactionAlreadyExistsException;
import com.mendel.transactions.model.Transaction;
import com.mendel.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository repository;

    @Mock
    private TransactionPropagatorService propagatorService;

    private TransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TransactionServiceImpl(repository, propagatorService);
    }

    // --- createTransaction: happy paths ---

    @Test
    void createTransaction_rootTransaction_savesWithPropagatedTrueAndDoesNotPropagate() {
        TransactionRequest request = new TransactionRequest(10L, 5000.0, "cars", null);

        service.createTransaction(request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(10L);
        assertThat(saved.getAmount()).isEqualTo(5000.0);
        assertThat(saved.getType()).isEqualTo("cars");
        assertThat(saved.getParentId()).isEmpty();
        assertThat(saved.getPath()).isEqualTo("10");
        assertThat(saved.getAccumulatedSum()).isEqualTo(5000.0);
        assertThat(saved.isPropagated()).isTrue();

        verify(propagatorService, never()).propagate(any());
    }

    @Test
    void createTransaction_childTransaction_savesWithPropagatedFalseAndPropagates() {
        Transaction parent = Transaction.builder()
                .id(10L).amount(5000.0).type("cars").path("10").accumulatedSum(5000.0).propagated(true).build();
        when(repository.findById(10L)).thenReturn(Optional.of(parent));

        TransactionRequest request = new TransactionRequest(11L, 10000.0, "shopping", 10L);

        service.createTransaction(request);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(repository).save(captor.capture());
        Transaction saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo(11L);
        assertThat(saved.getParentId()).hasValue(10L);
        assertThat(saved.getPath()).isEqualTo("10/11");
        assertThat(saved.getAccumulatedSum()).isEqualTo(10000.0);
        assertThat(saved.isPropagated()).isFalse();

        verify(propagatorService).propagate(saved);
    }

    // --- createTransaction: validaciones ---

    @Test
    void createTransaction_invalidId_throwsInvalidTransactionException() {
        TransactionRequest request = new TransactionRequest(0L, 5000.0, "cars", null);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_negativeId_throwsInvalidTransactionException() {
        TransactionRequest request = new TransactionRequest(-1L, 5000.0, "cars", null);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_nullType_throwsInvalidTransactionException() {
        TransactionRequest request = new TransactionRequest(10L, 5000.0, null, null);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_blankType_throwsInvalidTransactionException() {
        TransactionRequest request = new TransactionRequest(10L, 5000.0, "  ", null);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(InvalidTransactionException.class);

        verify(repository, never()).save(any());
    }

    // --- createTransaction: errores de dominio ---

    @Test
    void createTransaction_parentNotFound_throwsParentNotFoundException() {
        when(repository.findById(99L)).thenReturn(Optional.empty());
        TransactionRequest request = new TransactionRequest(11L, 1000.0, "shopping", 99L);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(ParentNotFoundException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void createTransaction_duplicateId_throwsTransactionAlreadyExistsException() {
        doThrow(new TransactionAlreadyExistsException(10L)).when(repository).save(any());
        TransactionRequest request = new TransactionRequest(10L, 5000.0, "cars", null);

        assertThatThrownBy(() -> service.createTransaction(request))
                .isInstanceOf(TransactionAlreadyExistsException.class);
    }
}
