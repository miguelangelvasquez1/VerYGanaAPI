package com.verygana2.controllers.kushki;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.services.interfaces.finance.PayoutService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests de {@link KushkiWebhookController}: sigue la "regla de oro" de todo
 * webhook — SIEMPRE responder 200, incluso ante payload inválido o error
 * interno, para que Kushki no reintente indefinidamente. Solo los estados
 * terminales (APPROVED/DECLINED/FAILED) se delegan al servicio.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KushkiWebhookController")
class KushkiWebhookControllerTest {

    @Mock private PayoutService payoutService;

    private KushkiWebhookController controller;

    @BeforeEach
    void setUp() {
        controller = new KushkiWebhookController(payoutService, new ObjectMapper());
    }

    @Test
    @DisplayName("evento APPROVED: deserializa y delega en el service, responde 200")
    void approvedEvent_delegatesAndReturns200() {
        String body = "{\"transferId\":\"tr_1\",\"merchantTransferReference\":\"VG-PAYOUT-1\",\"status\":\"APPROVED\"}";

        var response = controller.handleWebhook(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService).handleKushkiWebhook(any(), any());
    }

    @Test
    @DisplayName("estado no terminal (PENDING): no delega en el service, pero igual responde 200")
    void nonTerminalEvent_skipsServiceButReturns200() {
        String body = "{\"transferId\":\"tr_1\",\"status\":\"PENDING\"}";

        var response = controller.handleWebhook(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleKushkiWebhook(any(), any());
    }

    @Test
    @DisplayName("JSON inválido: no lanza excepción, responde 200 igual")
    void invalidJson_returns200WithoutThrowing() {
        var response = controller.handleWebhook("{not valid json");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(payoutService, never()).handleKushkiWebhook(any(), any());
    }

    @Test
    @DisplayName("el service lanza una excepción procesando el evento: igual responde 200 (nunca reintentos infinitos)")
    void serviceThrows_stillReturns200() {
        String body = "{\"transferId\":\"tr_1\",\"merchantTransferReference\":\"VG-PAYOUT-1\",\"status\":\"DECLINED\"}";
        doThrow(new RuntimeException("DB caída")).when(payoutService).handleKushkiWebhook(any(), any());

        var response = controller.handleWebhook(body);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
