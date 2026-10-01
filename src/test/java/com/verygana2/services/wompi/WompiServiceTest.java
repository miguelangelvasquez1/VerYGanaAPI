package com.verygana2.services.wompi;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.wompi.WompiConfig;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO.WompiTransactionData;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.repositories.finance.WompiTransactionRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WompiService}: la orquestación de negocio sobre Wompi
 * (construir la URL del checkout, verificar procesamiento, reconciliar por
 * referencia y actualizar el registro local desde un webhook). Se mockean
 * {@link WompiClient}, {@link WompiConfig} y {@link WompiTransactionRepository}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WompiService")
class WompiServiceTest {

    @Mock private WompiClient wompiClient;
    @Mock private WompiConfig wompiConfig;
    @Mock private WompiTransactionRepository wompiTransactionRepository;

    private WompiService service;

    @BeforeEach
    void setUp() {
        service = new WompiService(wompiClient, wompiConfig, wompiTransactionRepository);
    }

    private WompiTransaction transaction(String reference, WompiTransactionStatus status) {
        return WompiTransaction.builder()
                .id(UUID.randomUUID())
                .wompiId("PENDING-" + reference)
                .type(WompiTransactionType.CHARGE_COPAYMENT)
                .amountInCents(100000L)
                .currency("COP")
                .status(status)
                .reference(reference)
                .build();
    }

    @Nested
    @DisplayName("createCheckoutUrl")
    class CreateCheckoutUrl {

        @Test
        @DisplayName("camino feliz: genera la URL firmada, persiste el registro PENDING y retorna la respuesta")
        void happyPath_generatesUrlAndPersistsPendingRecord() {
            when(wompiClient.generateIntegrityHash(anyString(), any(), anyString())).thenReturn("hash123");
            when(wompiConfig.getCheckoutBaseUrl()).thenReturn("https://checkout.wompi.co/p/");
            when(wompiConfig.getPublicKey()).thenReturn("pub_test_key");
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WompiCheckoutRequestDTO request = WompiCheckoutRequestDTO.builder()
                    .reference("VG-COP-1")
                    .amountInCents(100000L)
                    .customerEmail("user@test.com")
                    .redirectUrl("https://app.verygana.com/resultado")
                    .build();

            WompiCheckoutResponseDTO response = service.createCheckoutUrl(request, WompiTransactionType.CHARGE_COPAYMENT);

            assertThat(response.getCheckoutUrl()).startsWith("https://checkout.wompi.co/p/");
            assertThat(response.getCheckoutUrl()).contains("public-key=pub_test_key");
            assertThat(response.getCheckoutUrl()).contains("signature:integrity=hash123");
            assertThat(response.getReference()).isEqualTo("VG-COP-1");
            assertThat(response.getAmountInCents()).isEqualTo(100000L);

            ArgumentCaptor<WompiTransaction> captor = ArgumentCaptor.forClass(WompiTransaction.class);
            verify(wompiTransactionRepository).save(captor.capture());
            WompiTransaction saved = captor.getValue();
            assertThat(saved.getReference()).isEqualTo("VG-COP-1");
            assertThat(saved.getStatus()).isEqualTo(WompiTransactionStatus.PENDING);
            assertThat(saved.getWompiId()).isEqualTo("PENDING-VG-COP-1");
            assertThat(saved.getType()).isEqualTo(WompiTransactionType.CHARGE_COPAYMENT);
        }

        @Test
        @DisplayName("customerEmail null: no lanza excepción y guarda un metadata con email vacío")
        void nullCustomerEmail_doesNotThrow() {
            when(wompiClient.generateIntegrityHash(anyString(), any(), anyString())).thenReturn("hash123");
            when(wompiConfig.getCheckoutBaseUrl()).thenReturn("https://checkout.wompi.co/p/");
            when(wompiConfig.getPublicKey()).thenReturn("pub_test_key");
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WompiCheckoutRequestDTO request = WompiCheckoutRequestDTO.builder()
                    .reference("VG-COP-2")
                    .amountInCents(50000L)
                    .customerEmail(null)
                    .redirectUrl("https://app.verygana.com/resultado")
                    .build();

            WompiCheckoutResponseDTO response = service.createCheckoutUrl(request, WompiTransactionType.CHARGE_COPAYMENT);

            assertThat(response).isNotNull();
            ArgumentCaptor<WompiTransaction> captor = ArgumentCaptor.forClass(WompiTransaction.class);
            verify(wompiTransactionRepository).save(captor.capture());
            assertThat(captor.getValue().getMetadata()).containsEntry("customer_email", "");
        }
    }

    @Nested
    @DisplayName("isAlreadyProcessed")
    class IsAlreadyProcessed {

        @Test
        @DisplayName("transacción existente con status distinto de PENDING: true")
        void existingNonPendingTransaction_returnsTrue() {
            when(wompiTransactionRepository.findByWompiId("wompi-1"))
                    .thenReturn(Optional.of(transaction("VG-1", WompiTransactionStatus.APPROVED)));

            assertThat(service.isAlreadyProcessed("wompi-1")).isTrue();
        }

        @Test
        @DisplayName("transacción existente en PENDING: false (todavía no está procesada)")
        void existingPendingTransaction_returnsFalse() {
            when(wompiTransactionRepository.findByWompiId("wompi-1"))
                    .thenReturn(Optional.of(transaction("VG-1", WompiTransactionStatus.PENDING)));

            assertThat(service.isAlreadyProcessed("wompi-1")).isFalse();
        }

        @Test
        @DisplayName("sin registro local: false")
        void noLocalRecord_returnsFalse() {
            when(wompiTransactionRepository.findByWompiId("wompi-inexistente")).thenReturn(Optional.empty());

            assertThat(service.isAlreadyProcessed("wompi-inexistente")).isFalse();
        }
    }

    @Nested
    @DisplayName("reconcileByReference")
    class ReconcileByReference {

        @Test
        @DisplayName("Wompi tiene una transacción para esa referencia: retorna Optional con los datos")
        void wompiHasTransaction_returnsOptionalWithData() {
            WompiTransactionData data = new WompiTransactionData();
            data.setId("tx_1");
            data.setStatus("APPROVED");
            when(wompiClient.findTransactionByReference("VG-REF-1")).thenReturn(data);

            Optional<WompiTransactionData> result = service.reconcileByReference("VG-REF-1");

            assertThat(result).isPresent();
            assertThat(result.get().getId()).isEqualTo("tx_1");
        }

        @Test
        @DisplayName("Wompi no tiene ninguna transacción: retorna Optional vacío")
        void wompiHasNoTransaction_returnsEmptyOptional() {
            when(wompiClient.findTransactionByReference("VG-REF-NONE")).thenReturn(null);

            assertThat(service.reconcileByReference("VG-REF-NONE")).isEmpty();
        }
    }

    @Nested
    @DisplayName("updateTransactionFromWebhook")
    class UpdateTransactionFromWebhook {

        @Test
        @DisplayName("camino feliz APPROVED: actualiza wompiId, status, metadata y wompiCreatedAt")
        void approvedStatus_updatesAllFieldsIncludingWompiCreatedAt() {
            WompiTransaction existing = transaction("VG-REF-1", WompiTransactionStatus.PENDING);
            when(wompiTransactionRepository.findByReference("VG-REF-1")).thenReturn(Optional.of(existing));
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WompiTransaction result = service.updateTransactionFromWebhook(
                    "wompi-real-id", "VG-REF-1", "APPROVED", "2026-01-01T10:00:00Z", Map.of("raw", "payload"));

            assertThat(result.getWompiId()).isEqualTo("wompi-real-id");
            assertThat(result.getStatus()).isEqualTo(WompiTransactionStatus.APPROVED);
            assertThat(result.getMetadata()).containsEntry("raw", "payload");
            assertThat(result.getWompiCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("status DECLINED: actualiza status pero NO setea wompiCreatedAt")
        void declinedStatus_doesNotSetWompiCreatedAt() {
            WompiTransaction existing = transaction("VG-REF-1", WompiTransactionStatus.PENDING);
            when(wompiTransactionRepository.findByReference("VG-REF-1")).thenReturn(Optional.of(existing));
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WompiTransaction result = service.updateTransactionFromWebhook(
                    "wompi-real-id", "VG-REF-1", "DECLINED", "2026-01-01T10:00:00Z", Map.of());

            assertThat(result.getStatus()).isEqualTo(WompiTransactionStatus.DECLINED);
            assertThat(result.getWompiCreatedAt()).isNull();
        }

        @Test
        @DisplayName("status desconocido: se mapea a ERROR (no lanza excepción)")
        void unknownStatus_mapsToError() {
            WompiTransaction existing = transaction("VG-REF-1", WompiTransactionStatus.PENDING);
            when(wompiTransactionRepository.findByReference("VG-REF-1")).thenReturn(Optional.of(existing));
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WompiTransaction result = service.updateTransactionFromWebhook(
                    "wompi-real-id", "VG-REF-1", "SOME_UNKNOWN_STATUS", null, Map.of());

            assertThat(result.getStatus()).isEqualTo(WompiTransactionStatus.ERROR);
        }

        @Test
        @DisplayName("referencia desconocida: lanza IllegalArgumentException y no guarda nada")
        void unknownReference_throwsIllegalArgumentException() {
            when(wompiTransactionRepository.findByReference("VG-REF-INEXISTENTE")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateTransactionFromWebhook(
                    "wompi-real-id", "VG-REF-INEXISTENTE", "APPROVED", null, Map.of()))
                    .isInstanceOf(IllegalArgumentException.class);

            verify(wompiTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("wompiCreatedAt con formato inválido: no lanza, deja el campo en null")
        void malformedWompiCreatedAt_doesNotThrowAndLeavesFieldNull() {
            WompiTransaction existing = transaction("VG-REF-1", WompiTransactionStatus.PENDING);
            when(wompiTransactionRepository.findByReference("VG-REF-1")).thenReturn(Optional.of(existing));
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            WompiTransaction result = service.updateTransactionFromWebhook(
                    "wompi-real-id", "VG-REF-1", "APPROVED", "not-a-valid-date", Map.of());

            assertThat(result.getWompiCreatedAt()).isNull();
        }
    }
}
