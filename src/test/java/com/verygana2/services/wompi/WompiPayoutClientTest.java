package com.verygana2.services.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutBalanceResponseDTO;
import com.verygana2.dtos.wompi.WompiPayoutRequestDTO;
import com.verygana2.dtos.wompi.WompiPayoutResponseDTO;
import com.verygana2.exceptions.wompi.WompiApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WompiPayoutClient}: el cliente HTTP hacia la API de
 * Pagos a Terceros de Wompi. El {@link WebClient} se mockea con deep-stubs
 * para simular su API fluida (get/post → uri → retrieve → bodyToMono →
 * block) sin necesitar una conexión real — cubre el camino feliz, los
 * errores HTTP, y la validación de firma del webhook.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WompiPayoutClient")
class WompiPayoutClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private WebClient webClient;
    @Mock private WompiPayoutConfig wompiPayoutConfig;

    private WompiPayoutClient client;

    @BeforeEach
    void setUp() {
        client = new WompiPayoutClient(webClient, wompiPayoutConfig, new ObjectMapper());
    }

    private WompiPayoutBalanceResponseDTO.Account account(String id, long balanceInCents) {
        WompiPayoutBalanceResponseDTO.Account acc = new WompiPayoutBalanceResponseDTO.Account();
        acc.setId(id);
        acc.setBalanceInCents(balanceInCents);
        return acc;
    }

    @Test
    @DisplayName("getBalance: retorna la cuenta que coincide con el accountId configurado")
    void getBalance_returnsMatchingAccount() {
        WompiPayoutBalanceResponseDTO response = new WompiPayoutBalanceResponseDTO();
        response.setData(List.of(account("acc_1", 100_000L), account("acc_2", 200_000L)));

        when(wompiPayoutConfig.getAccountId()).thenReturn("acc_2");
        when(webClient.get().uri(anyString()).retrieve()
                .bodyToMono(WompiPayoutBalanceResponseDTO.class).block())
                .thenReturn(response);

        assertThat(client.getBalance().getBalanceInCents()).isEqualTo(200_000L);
    }

    @Test
    @DisplayName("getBalance: lista vacía lanza WompiApiException")
    void getBalance_emptyList_throwsWompiApiException() {
        WompiPayoutBalanceResponseDTO response = new WompiPayoutBalanceResponseDTO();
        response.setData(List.of());

        when(webClient.get().uri(anyString()).retrieve()
                .bodyToMono(WompiPayoutBalanceResponseDTO.class).block())
                .thenReturn(response);

        assertThatThrownBy(() -> client.getBalance()).isInstanceOf(WompiApiException.class);
    }

    @Test
    @DisplayName("getBalance: error HTTP de Wompi se envuelve en WompiApiException")
    void getBalance_httpError_wrapsInWompiApiException() {
        WebClientResponseException httpError = WebClientResponseException.create(
                500, "Internal Server Error", null, null, null);
        when(webClient.get().uri(anyString()).retrieve()
                .bodyToMono(WompiPayoutBalanceResponseDTO.class).block())
                .thenThrow(httpError);

        assertThatThrownBy(() -> client.getBalance()).isInstanceOf(WompiApiException.class);
    }

    /**
     * El deep-stub automático de Mockito no resuelve bien el tipo de retorno
     * genérico auto-referenciado de {@code header(String, String...)} (falla
     * con NullPointer al intentar encadenar). Se crea explícitamente el mock
     * intermedio y se le dice que {@code header(...)} se devuelve a sí mismo,
     * y de ahí en adelante el resto de la cadena sí puede seguir siendo deep-stub.
     */
    private WebClient.RequestBodySpec mockPostChain() {
        WebClient.RequestBodySpec bodySpec = mock(WebClient.RequestBodySpec.class, Answers.RETURNS_DEEP_STUBS);
        when(webClient.post().uri(anyString())).thenReturn(bodySpec);
        when(bodySpec.header(anyString(), any())).thenReturn(bodySpec);
        return bodySpec;
    }

    @Test
    @DisplayName("createPayout: retorna la respuesta en el camino feliz")
    void createPayout_returnsResponse() {
        WompiPayoutResponseDTO expected = new WompiPayoutResponseDTO();
        expected.setStatus(201);
        expected.setCode("OK");
        WompiPayoutResponseDTO.PayoutData data = new WompiPayoutResponseDTO.PayoutData();
        data.setPayoutId("wp_123");
        data.setSuccess(1);
        data.setFailed(0);
        expected.setData(data);
        WompiPayoutRequestDTO request = WompiPayoutRequestDTO.builder().reference("VG-PAYOUT-1").build();

        when(mockPostChain().bodyValue(any()).retrieve()
                .bodyToMono(WompiPayoutResponseDTO.class).block())
                .thenReturn(expected);

        assertThat(client.createPayout(request).getPayoutId()).isEqualTo("wp_123");
    }

    @Test
    @DisplayName("createPayout: respuesta nula lanza WompiApiException")
    void createPayout_nullResponse_throwsWompiApiException() {
        WompiPayoutRequestDTO request = WompiPayoutRequestDTO.builder().reference("VG-PAYOUT-1").build();

        when(mockPostChain().bodyValue(any()).retrieve()
                .bodyToMono(WompiPayoutResponseDTO.class).block())
                .thenReturn(null);

        assertThatThrownBy(() -> client.createPayout(request)).isInstanceOf(WompiApiException.class);
    }

    @Test
    @DisplayName("createPayout: error HTTP de Wompi se envuelve en WompiApiException")
    void createPayout_httpError_wrapsInWompiApiException() {
        WompiPayoutRequestDTO request = WompiPayoutRequestDTO.builder().reference("VG-PAYOUT-1").build();
        WebClientResponseException httpError = WebClientResponseException.create(
                400, "Bad Request", null, null, null);

        when(mockPostChain().bodyValue(any()).retrieve()
                .bodyToMono(WompiPayoutResponseDTO.class).block())
                .thenThrow(httpError);

        assertThatThrownBy(() -> client.createPayout(request)).isInstanceOf(WompiApiException.class);
    }

    @Test
    @DisplayName("isValidWebhookSignature: checksum correcto retorna true")
    void isValidWebhookSignature_correctChecksum_returnsTrue() throws Exception {
        when(wompiPayoutConfig.getEventsKey()).thenReturn("test_events_key");

        long timestamp = 1747673128600L;
        String raw = "tx_1" + "APPROVED" + timestamp + "test_events_key";
        String checksum = sha256(raw);

        String body = """
                {
                  "event": "transaction.updated",
                  "data": { "transaction": { "id": "tx_1", "status": "APPROVED" } },
                  "signature": { "checksum": "%s", "properties": ["transaction.id", "transaction.status"] },
                  "timestamp": %d
                }
                """.formatted(checksum, timestamp);

        assertThat(client.isValidWebhookSignature(body, checksum)).isTrue();
    }

    @Test
    @DisplayName("isValidWebhookSignature: checksum incorrecto retorna false")
    void isValidWebhookSignature_wrongChecksum_returnsFalse() {
        when(wompiPayoutConfig.getEventsKey()).thenReturn("test_events_key");

        String body = """
                {
                  "event": "transaction.updated",
                  "data": { "transaction": { "id": "tx_1", "status": "APPROVED" } },
                  "signature": { "checksum": "whatever", "properties": ["transaction.id", "transaction.status"] },
                  "timestamp": 1747673128600
                }
                """;

        assertThat(client.isValidWebhookSignature(body, "checksum-invalido")).isFalse();
    }

    @Test
    @DisplayName("isValidWebhookSignature: payload sin timestamp retorna false")
    void isValidWebhookSignature_missingTimestamp_returnsFalse() {
        String body = """
                {
                  "event": "transaction.updated",
                  "data": { "transaction": { "id": "tx_1", "status": "APPROVED" } },
                  "signature": { "checksum": "abc", "properties": ["transaction.id"] }
                }
                """;

        assertThat(client.isValidWebhookSignature(body, "abc")).isFalse();
    }

    private String sha256(String input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
