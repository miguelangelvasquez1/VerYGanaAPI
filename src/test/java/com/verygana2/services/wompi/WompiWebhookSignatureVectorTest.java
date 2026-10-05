package com.verygana2.services.wompi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.wompi.WompiConfig;

/**
 * Vector de firma del webhook de Wompi compartido con k6.
 *
 * <p>El simulador de pagos de la prueba de carga (stress-tests/k6/lib/wompi.js) arma y firma el
 * evento {@code transaction.updated}. Para que ese webhook se pruebe contra el validador real,
 * este test y {@code stress-tests/k6/selftest.js} usan el mismo vector. Si se cambia aquí, hay que
 * cambiarlo allá.
 *
 * <pre>
 * eventsKey  = loadtest-wompi-events
 * timestamp  = 1668097749
 * properties = [transaction.id, transaction.status, transaction.amount_in_cents]
 * transaction: id=lt-tx-X1, status=APPROVED, amount_in_cents=300000
 * raw        = "lt-tx-X1" + "APPROVED" + "300000" + "1668097749" + eventsKey
 * checksum   = SHA256(raw) = 15028db4ebf404adbe270b9aab719cfc3493172e5ca911e32aca545d665b120e
 * </pre>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Vector de firma del webhook de Wompi")
class WompiWebhookSignatureVectorTest {

    private static final String EVENTS_KEY = "loadtest-wompi-events";
    private static final String CHECKSUM = "15028db4ebf404adbe270b9aab719cfc3493172e5ca911e32aca545d665b120e";

    private static final String BODY_TEMPLATE = """
            {"event":"transaction.updated","data":{"transaction":{"id":"lt-tx-X1","status":"APPROVED",\
            "amount_in_cents":%d,"reference":"R1"}},"environment":"test",\
            "signature":{"properties":["transaction.id","transaction.status","transaction.amount_in_cents"],\
            "checksum":"%s"},"timestamp":1668097749,"sent_at":"2022-11-10T16:29:09.000Z"}""";

    @Mock private WebClient webClient;
    @Mock private WompiConfig wompiConfig;

    private WompiClient client;

    @BeforeEach
    void setUp() {
        when(wompiConfig.getEventsKey()).thenReturn(EVENTS_KEY);
        client = new WompiClient(webClient, wompiConfig, new ObjectMapper());
    }

    @Test
    @DisplayName("el vector conocido se acepta")
    void knownVectorIsAccepted() {
        String body = BODY_TEMPLATE.formatted(300000, CHECKSUM);

        assertThat(client.isValidWebhookSignature(body, CHECKSUM)).isTrue();
    }

    @Test
    @DisplayName("si cambia el monto, la firma se rechaza")
    void tamperedAmountIsRejected() {
        String body = BODY_TEMPLATE.formatted(300001, CHECKSUM);

        assertThat(client.isValidWebhookSignature(body, CHECKSUM)).isFalse();
    }
}
