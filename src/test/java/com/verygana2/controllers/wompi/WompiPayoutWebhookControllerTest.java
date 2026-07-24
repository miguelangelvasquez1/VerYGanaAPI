package com.verygana2.controllers.wompi;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.wompi.WompiPayoutClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WompiPayoutWebhookController}: sigue la "regla de oro" de
 * todo webhook — SIEMPRE responder 200, incluso ante payload inválido, firma
 * inválida o error interno, para que Wompi no reintente indefinidamente.
 * Solo "transaction.updated" en estado terminal y con firma válida se
 * delega al servicio. La validación de firma en sí (WompiPayoutClient) se
 * mockea aquí — se prueba por separado en WompiPayoutClientTest.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WompiPayoutWebhookController")
class WompiPayoutWebhookControllerTest {

    @Mock private WompiPayoutClient wompiPayoutClient;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private PayoutService payoutService;

    private WompiPayoutWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new WompiPayoutWebhookController(
                wompiPayoutClient, wompiTransactionRepository, payoutService, new ObjectMapper());
    }

    private String transactionBody(String status, String reference) {
        return """
                {
                  "event": "transaction.updated",
                  "data": { "transaction": { "id": "tx_1", "reference": "%s", "status": "%s" } },
                  "signature": { "checksum": "sig_ok", "properties": ["transaction.id", "transaction.status"] },
                  "timestamp": 1747673128600
                }
                """.formatted(reference, status);
    }

    @Test
    @DisplayName("firma válida + APPROVED + referencia conocida: actualiza la WompiTransaction y delega en el service")
    void validApproved_updatesAndDelegates() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), eq("x-checksum"))).thenReturn(true);
        WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                .status(WompiTransactionStatus.PENDING).build();
        when(wompiTransactionRepository.findByReference("VG-PAYOUT-1")).thenReturn(Optional.of(tx));
        when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.handleWebhook(transactionBody("APPROVED", "VG-PAYOUT-1"), "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(tx.getStatus()).isEqualTo(WompiTransactionStatus.APPROVED);
        verify(payoutService).handleWompiResult(tx.getId());
    }

    @Test
    @DisplayName("firma inválida: no procesa el evento, responde 200")
    void invalidSignature_skipsProcessing() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), any())).thenReturn(false);

        var response = controller.handleWebhook(transactionBody("APPROVED", "VG-PAYOUT-1"), "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleWompiResult(any());
    }

    @Test
    @DisplayName("header de checksum ausente: cae al checksum del body")
    void missingChecksumHeader_fallsBackToBodySignature() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), eq("sig_ok"))).thenReturn(true);
        WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                .status(WompiTransactionStatus.PENDING).build();
        when(wompiTransactionRepository.findByReference("VG-PAYOUT-1")).thenReturn(Optional.of(tx));
        when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var response = controller.handleWebhook(transactionBody("APPROVED", "VG-PAYOUT-1"), null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService).handleWompiResult(tx.getId());
    }

    @Test
    @DisplayName("estado no terminal (PENDING): no delega en el service, pero igual responde 200")
    void nonTerminalEvent_skipsServiceButReturns200() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), any())).thenReturn(true);

        var response = controller.handleWebhook(transactionBody("PENDING", "VG-PAYOUT-1"), "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleWompiResult(any());
    }

    @Test
    @DisplayName("evento payout.updated (a nivel de lote): se ignora, responde 200")
    void batchLevelEvent_ignored() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), any())).thenReturn(true);
        String body = """
                {
                  "event": "payout.updated",
                  "data": { "payout": { "id": "p_1", "status": "TOTAL_PAYMENT" } },
                  "signature": { "checksum": "sig_ok", "properties": ["payout.id"] },
                  "timestamp": 1747673128600
                }
                """;

        var response = controller.handleWebhook(body, "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleWompiResult(any());
    }

    @Test
    @DisplayName("referencia desconocida: no falla, simplemente ignora el evento")
    void unknownReference_ignoresSilently() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), any())).thenReturn(true);
        when(wompiTransactionRepository.findByReference("VG-PAYOUT-999")).thenReturn(Optional.empty());
        when(wompiTransactionRepository.findByWompiId("tx_1")).thenReturn(Optional.empty());

        var response = controller.handleWebhook(transactionBody("APPROVED", "VG-PAYOUT-999"), "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleWompiResult(any());
    }

    @Test
    @DisplayName("JSON inválido: no lanza excepción, responde 200 igual")
    void invalidJson_returns200WithoutThrowing() {
        var response = controller.handleWebhook("{not valid json", "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleWompiResult(any());
    }

    @Test
    @DisplayName("el service lanza una excepción procesando el evento: igual responde 200 (nunca reintentos infinitos)")
    void serviceThrows_stillReturns200() {
        when(wompiPayoutClient.isValidWebhookSignature(any(), any())).thenReturn(true);
        WompiTransaction tx = WompiTransaction.builder().id(UUID.randomUUID())
                .status(WompiTransactionStatus.PENDING).build();
        when(wompiTransactionRepository.findByReference("VG-PAYOUT-1")).thenReturn(Optional.of(tx));
        when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new RuntimeException("DB caída")).when(payoutService).handleWompiResult(any());

        var response = controller.handleWebhook(transactionBody("DECLINED", "VG-PAYOUT-1"), "x-checksum");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
