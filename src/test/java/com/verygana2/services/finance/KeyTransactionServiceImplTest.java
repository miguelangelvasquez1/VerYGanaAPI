package com.verygana2.services.finance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.mappers.finance.KeyTransactionMapper;
import com.verygana2.repositories.finance.KeyTransactionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link KeyTransactionServiceImpl}: los totales (ganadas/usadas/
 * vencidas) deben responder 0, no null, cuando el consumidor no tiene
 * movimientos todavía — el repositorio real haría un SUM sobre cero filas.
 * Los totales se guardan en centavos y se devuelven en llaves enteras.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KeyTransactionServiceImpl")
class KeyTransactionServiceImplTest {

    private static final long KEY_VALUE_CENTS = 1000L;

    @Mock private KeyTransactionRepository keyTransactionRepository;
    @Mock private KeyTransactionMapper keyTransactionMapper;

    private KeyTransactionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new KeyTransactionServiceImpl(keyTransactionRepository, keyTransactionMapper);
        ReflectionTestUtils.setField(service, "keyValueCents", KEY_VALUE_CENTS);
    }

    @Test
    @DisplayName("getTotalEarnedKeys: sin movimientos (SUM null) retorna 0")
    void getTotalEarnedKeys_nullSumReturnsZero() {
        when(keyTransactionRepository.sumTotalEarnedKeysCents(9L)).thenReturn(null);

        assertThat(service.getTotalEarnedKeys(9L)).isZero();
    }

    @Test
    @DisplayName("getTotalEarnedKeys: con movimientos retorna la suma real convertida a llaves")
    void getTotalEarnedKeys_returnsSum() {
        when(keyTransactionRepository.sumTotalEarnedKeysCents(9L)).thenReturn(500_000L);

        assertThat(service.getTotalEarnedKeys(9L)).isEqualTo(500L);
    }

    @Test
    @DisplayName("getTotalUsedKeys: sin movimientos retorna 0")
    void getTotalUsedKeys_nullSumReturnsZero() {
        when(keyTransactionRepository.sumTotalUsedKeysCents(9L)).thenReturn(null);

        assertThat(service.getTotalUsedKeys(9L)).isZero();
    }

    @Test
    @DisplayName("getTotalExpiredKeys: sin movimientos retorna 0")
    void getTotalExpiredKeys_nullSumReturnsZero() {
        when(keyTransactionRepository.sumTotalExpiredKeysCents(9L)).thenReturn(null);

        assertThat(service.getTotalExpiredKeys(9L)).isZero();
    }
}
