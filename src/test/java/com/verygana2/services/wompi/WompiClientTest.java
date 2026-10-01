package com.verygana2.services.wompi;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.wompi.WompiConfig;
import com.verygana2.dtos.wompi.WompiTransactionListResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionRequestDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO.WompiTransactionData;
import com.verygana2.exceptions.wompi.WompiApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link WompiClient}: el cliente HTTP hacia la API de transacciones
 * de Wompi. El {@link WebClient} se mockea con deep-stubs para simular su API
 * fluida (get/post -> uri -> retrieve -> bodyToMono -> block) sin necesitar
 * una conexión real — cubre el camino feliz, los errores HTTP y la
 * validación de la firma del webhook.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WompiClient")
class WompiClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private WebClient webClient;
    @Mock private WompiConfig wompiConfig;

    private WompiClient client;

    @BeforeEach
    void setUp() {
        client = new WompiClient(webClient, wompiConfig, new ObjectMapper());
    }

    private WompiTransactionData transactionData(String id, String status, String createdAt) {
        WompiTransactionData data = new WompiTransactionData();
        data.setId(id);
        data.setStatus(status);
        data.setCreatedAt(createdAt);
        return data;
    }

    @Nested
    @DisplayName("createTransaction")
    class CreateTransaction {

        @Test
        @DisplayName("camino feliz: la respuesta 200 se parsea correctamente")
        void happyPath_parsesResponse() {
            WompiTransactionResponseDTO response = new WompiTransactionResponseDTO();
            response.setData(transactionData("tx_1", "PENDING", "2026-01-01T00:00:00Z"));
            WompiTransactionRequestDTO request = WompiTransactionRequestDTO.builder()
                    .reference("VG-REF-1").amountInCents(100000L).build();

            when(webClient.post().uri("/transactions").bodyValue(request).retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class).block())
                    .thenReturn(response);

            WompiTransactionResponseDTO result = client.createTransaction(request);

            assertThat(result.getData().getId()).isEqualTo("tx_1");
            assertThat(result.getData().getStatus()).isEqualTo("PENDING");
        }

        @Test
        @DisplayName("error HTTP de Wompi se envuelve en WompiApiException con el status original")
        void httpError_wrapsInWompiApiException() {
            WompiTransactionRequestDTO request = WompiTransactionRequestDTO.builder()
                    .reference("VG-REF-1").amountInCents(100000L).build();
            WebClientResponseException httpError = WebClientResponseException.create(
                    400, "Bad Request", null, null, null);

            when(webClient.post().uri("/transactions").bodyValue(request).retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class).block())
                    .thenThrow(httpError);

            assertThatThrownBy(() -> client.createTransaction(request))
                    .isInstanceOf(WompiApiException.class)
                    .satisfies(e -> assertThat(((WompiApiException) e).getWompiStatusCode()).isEqualTo(400));
        }
    }

    @Nested
    @DisplayName("getTransaction")
    class GetTransaction {

        @Test
        @DisplayName("camino feliz: retorna la transacción consultada")
        void happyPath_returnsTransaction() {
            WompiTransactionResponseDTO response = new WompiTransactionResponseDTO();
            response.setData(transactionData("tx_1", "APPROVED", "2026-01-01T00:00:00Z"));

            when(webClient.get().uri("/transactions/{id}", "tx_1").retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class).block())
                    .thenReturn(response);

            assertThat(client.getTransaction("tx_1").getData().getStatus()).isEqualTo("APPROVED");
        }

        @Test
        @DisplayName("404 de Wompi se traduce a WompiApiException con mensaje de 'no encontrada'")
        void notFound_throwsWompiApiExceptionWithSpecificMessage() {
            WebClientResponseException notFound = WebClientResponseException.create(
                    404, "Not Found", null, null, null);

            when(webClient.get().uri("/transactions/{id}", "tx_missing").retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class).block())
                    .thenThrow(notFound);

            assertThatThrownBy(() -> client.getTransaction("tx_missing"))
                    .isInstanceOf(WompiApiException.class)
                    .satisfies(e -> {
                        WompiApiException ex = (WompiApiException) e;
                        assertThat(ex.getWompiStatusCode()).isEqualTo(404);
                        assertThat(ex.getMessage()).contains("no encontrada");
                    });
        }

        @Test
        @DisplayName("error 5xx distinto de 404 se envuelve con el mensaje genérico")
        void serverError_wrapsWithGenericMessage() {
            WebClientResponseException serverError = WebClientResponseException.create(
                    500, "Internal Server Error", null, null, null);

            when(webClient.get().uri("/transactions/{id}", "tx_1").retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class).block())
                    .thenThrow(serverError);

            assertThatThrownBy(() -> client.getTransaction("tx_1"))
                    .isInstanceOf(WompiApiException.class)
                    .satisfies(e -> assertThat(((WompiApiException) e).getWompiStatusCode()).isEqualTo(500));
        }
    }

    @Nested
    @DisplayName("findTransactionByReference")
    class FindTransactionByReference {

        @Test
        @DisplayName("camino feliz: retorna la transacción más reciente entre varias con la misma referencia")
        void happyPath_returnsMostRecent() {
            WompiTransactionListResponseDTO response = new WompiTransactionListResponseDTO();
            response.setData(List.of(
                    transactionData("tx_old", "DECLINED", "2026-01-01T00:00:00Z"),
                    transactionData("tx_new", "APPROVED", "2026-01-02T00:00:00Z")));

            when(webClient.get().uri(any(java.util.function.Function.class)).retrieve()
                    .bodyToMono(WompiTransactionListResponseDTO.class).block())
                    .thenReturn(response);

            WompiTransactionData result = client.findTransactionByReference("VG-REF-1");

            assertThat(result.getId()).isEqualTo("tx_new");
        }

        @Test
        @DisplayName("sin resultados: retorna null (no lanza excepción)")
        void noResults_returnsNull() {
            WompiTransactionListResponseDTO response = new WompiTransactionListResponseDTO();
            response.setData(List.of());

            when(webClient.get().uri(any(java.util.function.Function.class)).retrieve()
                    .bodyToMono(WompiTransactionListResponseDTO.class).block())
                    .thenReturn(response);

            assertThat(client.findTransactionByReference("VG-REF-NONE")).isNull();
        }

        @Test
        @DisplayName("404 de Wompi: retorna null en vez de lanzar (a diferencia de getTransaction)")
        void notFound_returnsNullInsteadOfThrowing() {
            WebClientResponseException notFound = WebClientResponseException.create(
                    404, "Not Found", null, null, null);

            when(webClient.get().uri(any(java.util.function.Function.class)).retrieve()
                    .bodyToMono(WompiTransactionListResponseDTO.class).block())
                    .thenThrow(notFound);

            assertThat(client.findTransactionByReference("VG-REF-1")).isNull();
        }

        @Test
        @DisplayName("error HTTP distinto de 404 se envuelve en WompiApiException")
        void otherHttpError_wrapsInWompiApiException() {
            WebClientResponseException serverError = WebClientResponseException.create(
                    503, "Service Unavailable", null, null, null);

            when(webClient.get().uri(any(java.util.function.Function.class)).retrieve()
                    .bodyToMono(WompiTransactionListResponseDTO.class).block())
                    .thenThrow(serverError);

            assertThatThrownBy(() -> client.findTransactionByReference("VG-REF-1"))
                    .isInstanceOf(WompiApiException.class);
        }
    }

    @Nested
    @DisplayName("generateIntegrityHash")
    class GenerateIntegrityHash {

        @Test
        @DisplayName("arma el hash SHA-256 a partir de reference + amountInCents + currency + integritySecret")
        void buildsHashFromConcatenatedFields() {
            when(wompiConfig.getIntegritySecret()).thenReturn("secret_test");

            String hash = client.generateIntegrityHash("VG-REF-1", 100000L, "COP");

            // Determinístico: mismos inputs siempre producen el mismo hash SHA-256 hex de 64 chars
            assertThat(hash).hasSize(64);
            assertThat(hash).isEqualTo(client.generateIntegrityHash("VG-REF-1", 100000L, "COP"));
        }

        @Test
        @DisplayName("distintos inputs producen distinto hash")
        void differentInputs_produceDifferentHash() {
            when(wompiConfig.getIntegritySecret()).thenReturn("secret_test");

            String hash1 = client.generateIntegrityHash("VG-REF-1", 100000L, "COP");
            String hash2 = client.generateIntegrityHash("VG-REF-2", 100000L, "COP");

            assertThat(hash1).isNotEqualTo(hash2);
        }
    }

    @Nested
    @DisplayName("isValidWebhookSignature")
    class IsValidWebhookSignature {

        @Test
        @DisplayName("checksum correcto retorna true")
        void correctChecksum_returnsTrue() throws Exception {
            when(wompiConfig.getEventsKey()).thenReturn("events_key_test");

            long timestamp = 1747673128600L;
            String raw = "tx_1" + "APPROVED" + timestamp + "events_key_test";
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
        @DisplayName("checksum incorrecto retorna false")
        void wrongChecksum_returnsFalse() {
            when(wompiConfig.getEventsKey()).thenReturn("events_key_test");

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
        @DisplayName("payload sin timestamp retorna false, sin lanzar excepción")
        void missingTimestamp_returnsFalse() {
            String body = """
                    {
                      "event": "transaction.updated",
                      "data": { "transaction": { "id": "tx_1", "status": "APPROVED" } },
                      "signature": { "checksum": "abc", "properties": ["transaction.id"] }
                    }
                    """;

            assertThat(client.isValidWebhookSignature(body, "abc")).isFalse();
        }

        @Test
        @DisplayName("payload sin bloque signature retorna false")
        void missingSignatureBlock_returnsFalse() {
            String body = """
                    {
                      "event": "transaction.updated",
                      "data": { "transaction": { "id": "tx_1", "status": "APPROVED" } },
                      "timestamp": 1747673128600
                    }
                    """;

            assertThat(client.isValidWebhookSignature(body, "abc")).isFalse();
        }

        @Test
        @DisplayName("payload sin data.transaction (evento de un tipo no soportado) retorna false")
        void missingTransaction_returnsFalse() {
            // No se stubea wompiConfig.getEventsKey(): el método retorna false por
            // data.transaction == null antes de llegar a usar la eventsKey.
            String body = """
                    {
                      "event": "nequi_token.updated",
                      "data": { "some_other_object": {} },
                      "signature": { "checksum": "abc", "properties": ["transaction.id"] },
                      "timestamp": 1747673128600
                    }
                    """;

            assertThat(client.isValidWebhookSignature(body, "abc")).isFalse();
        }

        @Test
        @DisplayName("JSON malformado no lanza excepción: se captura y retorna false")
        void malformedJson_returnsFalseWithoutThrowing() {
            assertThat(client.isValidWebhookSignature("{ not valid json", "abc")).isFalse();
        }
    }

    private String sha256(String input) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hashBytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}
