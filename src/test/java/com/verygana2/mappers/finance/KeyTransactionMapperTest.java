package com.verygana2.mappers.finance;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.finance.responses.KeyTransactionResponseDTO;
import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.models.finance.KeyTransaction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests de {@link KeyTransactionMapper}: la conversión de deltas en centavos
 * (persistidos) a deltas en llaves (expuestos al cliente), gobernada por el
 * valor de una llave inyectado vía {@code @Value}. Se instancia la
 * implementación real generada por MapStruct y se setea el campo con
 * {@link ReflectionTestUtils} porque no hay contexto de Spring.
 */
@DisplayName("KeyTransactionMapper")
class KeyTransactionMapperTest {

    private KeyTransactionMapperImpl mapper;

    @BeforeEach
    void setUp() {
        mapper = new KeyTransactionMapperImpl();
        ReflectionTestUtils.setField(mapper, "keyValueCents", 1000L);
    }

    private KeyTransaction keyTransaction(Long purchaseDeltaCents, Long connectivityDeltaCents) {
        return KeyTransaction.builder()
                .id(UUID.randomUUID())
                .type(KeyTransactionType.CREDIT_INTERACTION)
                .purchaseKeysDeltaCents(purchaseDeltaCents)
                .connectivityKeysDeltaCents(connectivityDeltaCents)
                .reason("Interacción con anuncio")
                .referenceId(UUID.randomUUID())
                .build();
    }

    @Nested
    @DisplayName("centsToKeysDelta")
    class CentsToKeysDelta {

        @Test
        @DisplayName("delta exacto: 5000 cents con keyValueCents=1000 -> 5 keys")
        void exactDelta_dividesEvenly() {
            assertThat(mapper.centsToKeysDelta(5000L)).isEqualTo(5L);
        }

        @Test
        @DisplayName("delta que no divide exacto: 4999 cents con keyValueCents=1000 -> trunca a 4, no redondea")
        void inexactDelta_truncates() {
            assertThat(mapper.centsToKeysDelta(4999L)).isEqualTo(4L);
        }

        @Test
        @DisplayName("delta null -> null")
        void nullDelta_returnsNull() {
            assertThat(mapper.centsToKeysDelta(null)).isNull();
        }

        @Test
        @DisplayName("delta negativo (débito): el signo se preserva")
        void negativeDelta_preservesSign() {
            assertThat(mapper.centsToKeysDelta(-5000L)).isEqualTo(-5L);
        }

        @Test
        @DisplayName("delta negativo que no divide exacto: -4999 / 1000 trunca hacia cero (-4), no hacia -5")
        void negativeInexactDelta_truncatesTowardZero() {
            assertThat(mapper.centsToKeysDelta(-4999L)).isEqualTo(-4L);
        }

        @Test
        @DisplayName("keyValueCents distinto del default (500): el cálculo cambia proporcionalmente")
        void differentKeyValueCents_changesCalculationProportionally() {
            ReflectionTestUtils.setField(mapper, "keyValueCents", 500L);

            assertThat(mapper.centsToKeysDelta(5000L)).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("toKeyTransactionResponseDTO")
    class ToKeyTransactionResponseDTO {

        @Test
        @DisplayName("mapea purchaseKeysDeltaCents -> purchaseKeysDelta y connectivityKeysDeltaCents -> connectivityKeysDelta")
        void mapsBothDeltasThroughCentsToKeysDelta() {
            KeyTransaction kt = keyTransaction(5000L, 2000L);

            KeyTransactionResponseDTO dto = mapper.toKeyTransactionResponseDTO(kt);

            assertThat(dto.getPurchaseKeysDelta()).isEqualTo(5L);
            assertThat(dto.getConnectivityKeysDelta()).isEqualTo(2L);
        }

        @Test
        @DisplayName("confirmación de copago: el monto va en el delta bloqueado y los saldos posteriores salen en llaves")
        void mapsBlockedDeltaBalancesAndOrigin() {
            KeyTransaction kt = KeyTransaction.builder()
                    .id(UUID.randomUUID())
                    .type(KeyTransactionType.DEBIT_COPAYMENT)
                    .originType(com.verygana2.models.enums.finance.KeyOriginType.COPAYMENT)
                    .blockedPurchaseDeltaCents(-5000L)
                    .purchaseBalanceAfterCents(12_000L)
                    .connectivityBalanceAfterCents(3000L)
                    .reason("Pago con llaves: Netflix")
                    .referenceId(UUID.randomUUID())
                    .build();

            KeyTransactionResponseDTO dto = mapper.toKeyTransactionResponseDTO(kt);

            // El disponible ya bajó al reservar: aquí no se mueve.
            assertThat(dto.getPurchaseKeysDelta()).isNull();
            assertThat(dto.getBlockedPurchaseKeysDelta()).isEqualTo(-5L);
            assertThat(dto.getPurchaseKeysBalanceAfter()).isEqualTo(12L);
            assertThat(dto.getConnectivityKeysBalanceAfter()).isEqualTo(3L);
            assertThat(dto.getOriginType()).isEqualTo(com.verygana2.models.enums.finance.KeyOriginType.COPAYMENT);
        }

        @Test
        @DisplayName("movimiento anterior al Libro Mayor: sin saldos, y origen LEGACY")
        void legacyRowHasNoBalances() {
            KeyTransactionResponseDTO dto = mapper.toKeyTransactionResponseDTO(keyTransaction(5000L, null));

            assertThat(dto.getPurchaseKeysBalanceAfter()).isNull();
            assertThat(dto.getOriginType()).isEqualTo(com.verygana2.models.enums.finance.KeyOriginType.LEGACY);
        }

        @Test
        @DisplayName("delta null en la entidad se mapea a null en el DTO")
        void nullDeltaInEntity_mapsToNullInDto() {
            KeyTransaction kt = keyTransaction(null, 3000L);

            KeyTransactionResponseDTO dto = mapper.toKeyTransactionResponseDTO(kt);

            assertThat(dto.getPurchaseKeysDelta()).isNull();
            assertThat(dto.getConnectivityKeysDelta()).isEqualTo(3L);
        }

        @Test
        @DisplayName("entidad null -> DTO null")
        void nullEntity_returnsNull() {
            assertThat(mapper.toKeyTransactionResponseDTO(null)).isNull();
        }
    }
}
