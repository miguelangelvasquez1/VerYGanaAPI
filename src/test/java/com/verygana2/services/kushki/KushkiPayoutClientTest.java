package com.verygana2.services.kushki;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.verygana2.dtos.kushki.KushkiBalanceResponseDTO;
import com.verygana2.dtos.kushki.KushkiTokenRequestDTO;
import com.verygana2.dtos.kushki.KushkiTokenResponseDTO;
import com.verygana2.dtos.kushki.KushkiTransferRequestDTO;
import com.verygana2.dtos.kushki.KushkiTransferResponseDTO;
import com.verygana2.exceptions.kushki.KushkiApiException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link KushkiPayoutClient}: el cliente HTTP hacia la API de
 * Kushki. El {@link WebClient} se mockea con deep-stubs para simular su API
 * fluida (get/post → uri → retrieve → bodyToMono → block) sin necesitar una
 * conexión real — cubre tanto las respuestas exitosas como los 2 tipos de
 * error (HTTP de Kushki, y respuesta vacía/nula).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KushkiPayoutClient")
class KushkiPayoutClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private WebClient webClient;

    private KushkiPayoutClient client;

    @BeforeEach
    void setUp() {
        client = new KushkiPayoutClient(webClient);
    }

    @Test
    @DisplayName("getBalance: retorna el body deserializado en el camino feliz")
    void getBalance_returnsBody() {
        KushkiBalanceResponseDTO expected = new KushkiBalanceResponseDTO();
        expected.setAvailableBalance(1_000_000.0);
        when(webClient.get().uri(anyString()).retrieve().bodyToMono(KushkiBalanceResponseDTO.class).block())
                .thenReturn(expected);

        assertThat(client.getBalance()).isSameAs(expected);
    }

    @Test
    @DisplayName("getBalance: error HTTP de Kushki se envuelve en KushkiApiException")
    void getBalance_httpError_wrapsInKushkiApiException() {
        WebClientResponseException httpError = WebClientResponseException.create(
                500, "Internal Server Error", null, null, null);
        when(webClient.get().uri(anyString()).retrieve().bodyToMono(KushkiBalanceResponseDTO.class).block())
                .thenThrow(httpError);

        assertThatThrownBy(() -> client.getBalance()).isInstanceOf(KushkiApiException.class);
    }

    @Test
    @DisplayName("tokenizeAccount: retorna el token en el camino feliz")
    void tokenizeAccount_returnsToken() {
        KushkiTokenResponseDTO expected = new KushkiTokenResponseDTO();
        expected.setToken("tok_abc123");
        KushkiTokenRequestDTO request = KushkiTokenRequestDTO.builder().bankCode("1007").documentNumber("123").build();

        when(webClient.post().uri(anyString()).bodyValue(any()).retrieve()
                .bodyToMono(KushkiTokenResponseDTO.class).block())
                .thenReturn(expected);

        assertThat(client.tokenizeAccount(request).getToken()).isEqualTo("tok_abc123");
    }

    @Test
    @DisplayName("tokenizeAccount: token nulo en la respuesta lanza KushkiApiException")
    void tokenizeAccount_nullToken_throwsKushkiApiException() {
        KushkiTokenResponseDTO emptyResponse = new KushkiTokenResponseDTO(); // token null
        KushkiTokenRequestDTO request = KushkiTokenRequestDTO.builder().bankCode("1007").build();

        when(webClient.post().uri(anyString()).bodyValue(any()).retrieve()
                .bodyToMono(KushkiTokenResponseDTO.class).block())
                .thenReturn(emptyResponse);

        assertThatThrownBy(() -> client.tokenizeAccount(request)).isInstanceOf(KushkiApiException.class);
    }

    @Test
    @DisplayName("initiateTransfer: retorna la respuesta en el camino feliz")
    void initiateTransfer_returnsResponse() {
        KushkiTransferResponseDTO expected = new KushkiTransferResponseDTO();
        expected.setTransferId("tr_123");
        expected.setCode("000");
        KushkiTransferRequestDTO request = KushkiTransferRequestDTO.builder()
                .token("tok_123").merchantTransferReference("VG-PAYOUT-1").build();

        when(webClient.post().uri(anyString()).bodyValue(any()).retrieve()
                .bodyToMono(KushkiTransferResponseDTO.class).block())
                .thenReturn(expected);

        assertThat(client.initiateTransfer(request).getTransferId()).isEqualTo("tr_123");
    }

    @Test
    @DisplayName("initiateTransfer: respuesta nula lanza KushkiApiException")
    void initiateTransfer_nullResponse_throwsKushkiApiException() {
        KushkiTransferRequestDTO request = KushkiTransferRequestDTO.builder()
                .token("tok_123").merchantTransferReference("VG-PAYOUT-1").build();

        when(webClient.post().uri(anyString()).bodyValue(any()).retrieve()
                .bodyToMono(KushkiTransferResponseDTO.class).block())
                .thenReturn(null);

        assertThatThrownBy(() -> client.initiateTransfer(request)).isInstanceOf(KushkiApiException.class);
    }

    @Test
    @DisplayName("initiateTransfer: error HTTP de Kushki se envuelve en KushkiApiException")
    void initiateTransfer_httpError_wrapsInKushkiApiException() {
        KushkiTransferRequestDTO request = KushkiTransferRequestDTO.builder()
                .token("tok_123").merchantTransferReference("VG-PAYOUT-1").build();
        WebClientResponseException httpError = WebClientResponseException.create(
                400, "Bad Request", null, null, null);

        when(webClient.post().uri(anyString()).bodyValue(any()).retrieve()
                .bodyToMono(KushkiTransferResponseDTO.class).block())
                .thenThrow(httpError);

        assertThatThrownBy(() -> client.initiateTransfer(request)).isInstanceOf(KushkiApiException.class);
    }
}
