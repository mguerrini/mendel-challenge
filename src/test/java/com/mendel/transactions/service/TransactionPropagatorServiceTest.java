package com.mendel.transactions.service;

import com.mendel.transactions.exception.AlreadyPropagatedException;
import com.mendel.transactions.exception.VersionConflictException;
import com.mendel.transactions.model.Transaction;
import com.mendel.transactions.repository.AncestorUpdate;
import com.mendel.transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionPropagatorServiceTest {

    @Mock
    private TransactionRepository repository;

    private TransactionPropagatorService propagatorService;

    @BeforeEach
    void setUp() {
        propagatorService = new TransactionPropagatorService(repository);
    }

    @Test
    void propagate_noConflict_appliesPropagationOnce() {
        Transaction transaction = transaction(11L, 1000.0, "10/11", false);
        Transaction ancestor = transaction(10L, 5000.0, "10", true);

        when(repository.findAncestors("10/11")).thenReturn(List.of(ancestor));

        propagatorService.propagate(transaction);

        verify(repository, times(1)).findAncestors("10/11");
        verify(repository, times(1)).applyPropagation(eq(transaction), any());
    }

    @Test
    void propagate_computesNewAccumulatedSumAndPassesExpectedVersion() {
        Transaction transaction = transaction(11L, 1000.0, "10/11", false);
        Transaction ancestor = transaction(10L, 5000.0, "10", true);

        when(repository.findAncestors("10/11")).thenReturn(List.of(ancestor));

        propagatorService.propagate(transaction);

        ArgumentCaptor<List<AncestorUpdate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).applyPropagation(eq(transaction), captor.capture());

        AncestorUpdate update = captor.getValue().get(0);
        assertThat(update.transactionId()).isEqualTo(10L);
        assertThat(update.newAccumulatedSum()).isEqualTo(6000.0);
        assertThat(update.expectedVersion()).isEqualTo(0L);
    }

    @Test
    void propagate_versionConflict_retriesWithFreshAncestors() {
        Transaction transaction = transaction(11L, 1000.0, "10/11", false);
        Transaction ancestorV0 = transaction(10L, 5000.0, "10", true, 0L);
        Transaction ancestorV1 = transaction(10L, 6000.0, "10", true, 1L);

        when(repository.findAncestors("10/11"))
                .thenReturn(List.of(ancestorV0))
                .thenReturn(List.of(ancestorV1));

        doThrow(new VersionConflictException(10L, 0L, 1L))
                .doNothing()
                .when(repository).applyPropagation(eq(transaction), any());

        propagatorService.propagate(transaction);

        verify(repository, times(2)).findAncestors("10/11");
        verify(repository, times(2)).applyPropagation(eq(transaction), any());
    }

    @Test
    void propagate_versionConflict_recalculatesNewValueOnRetry() {
        Transaction transaction = transaction(11L, 1000.0, "10/11", false);
        Transaction ancestorV0 = transaction(10L, 5000.0, "10", true, 0L);
        Transaction ancestorV1 = transaction(10L, 6000.0, "10", true, 1L);

        when(repository.findAncestors("10/11"))
                .thenReturn(List.of(ancestorV0))
                .thenReturn(List.of(ancestorV1));

        doThrow(new VersionConflictException(10L, 0L, 1L))
                .doNothing()
                .when(repository).applyPropagation(eq(transaction), any());

        propagatorService.propagate(transaction);

        ArgumentCaptor<List<AncestorUpdate>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository, times(2)).applyPropagation(eq(transaction), captor.capture());

        assertThat(captor.getAllValues().get(0).get(0).newAccumulatedSum()).isEqualTo(6000.0);
        assertThat(captor.getAllValues().get(1).get(0).newAccumulatedSum()).isEqualTo(7000.0);
    }

    @Test
    void propagate_exhaustsRetries_throwsVersionConflictException() {
        Transaction transaction = transaction(11L, 1000.0, "10/11", false);
        Transaction ancestor = transaction(10L, 5000.0, "10", true);

        when(repository.findAncestors("10/11")).thenReturn(List.of(ancestor));
        doThrow(new VersionConflictException(10L, 0L, 1L))
                .when(repository).applyPropagation(any(), any());

        assertThatThrownBy(() -> propagatorService.propagate(transaction))
                .isInstanceOf(VersionConflictException.class);

        verify(repository, times(3)).findAncestors("10/11");
        verify(repository, times(3)).applyPropagation(any(), any());
    }

    @Test
    void propagate_alreadyPropagated_returnsWithoutRetrying() {
        Transaction transaction = transaction(11L, 1000.0, "10/11", false);
        Transaction ancestor = transaction(10L, 5000.0, "10", true);

        when(repository.findAncestors("10/11")).thenReturn(List.of(ancestor));
        doThrow(new AlreadyPropagatedException(11L))
                .when(repository).applyPropagation(any(), any());

        propagatorService.propagate(transaction);

        verify(repository, times(1)).applyPropagation(any(), any());
    }

    // --- helpers ---

    private Transaction transaction(long id, double amount, String path, boolean propagated) {
        return transaction(id, amount, path, propagated, 0L);
    }

    private Transaction transaction(long id, double amount, String path, boolean propagated, long version) {
        return Transaction.builder()
                .id(id)
                .amount(amount)
                .type("test")
                .path(path)
                .accumulatedSum(amount)
                .propagated(propagated)
                .version(version)
                .build();
    }
}
