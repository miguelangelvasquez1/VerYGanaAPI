package com.verygana2.services.kushki;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.verygana2.dtos.kushki.KushkiBalanceResponseDTO;
import com.verygana2.dtos.kushki.KushkiTokenRequestDTO;
import com.verygana2.dtos.kushki.KushkiTokenResponseDTO;
import com.verygana2.dtos.kushki.KushkiTransferRequestDTO;
import com.verygana2.dtos.kushki.KushkiTransferResponseDTO;
import com.verygana2.exceptions.kushki.KushkiApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class KushkiPayoutClient {

    private final WebClient webClient;

    public KushkiPayoutClient(@Qualifier("kushkiWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Consulta el balance disponible en la cuenta de dispersiones de Kushki.
     */
    public KushkiBalanceResponseDTO getBalance() {
        log.info("[KUSHKI] Consultando balance de dispersiones");
        try {
            return webClient.get()
                    .uri("/payouts/balance/v1")
                    .retrieve()
                    .bodyToMono(KushkiBalanceResponseDTO.class)
                    .block();
        } catch (WebClientResponseException e) {
            log.error("[KUSHKI] Error consultando balance: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new KushkiApiException("Error consultando balance Kushki: " + e.getMessage(), e);
        }
    }

    /**
     * Tokeniza la cuenta bancaria destino.
     * Debe llamarse antes de iniciar cada transferencia.
     */
    public KushkiTokenResponseDTO tokenizeAccount(KushkiTokenRequestDTO request) {
        log.info("[KUSHKI] Tokenizando cuenta destino: bankCode={}, docNumber={}",
                request.getBankCode(), request.getDocumentNumber());
        try {
            KushkiTokenResponseDTO response = webClient.post()
                    .uri("/payouts/transfer/v1/tokens")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(KushkiTokenResponseDTO.class)
                    .block();

            if (response == null || response.getToken() == null) {
                throw new KushkiApiException("Kushki devolvió token nulo para bankCode=" + request.getBankCode());
            }

            log.info("[KUSHKI] Token obtenido exitosamente");
            return response;

        } catch (WebClientResponseException e) {
            log.error("[KUSHKI] Error tokenizando cuenta: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new KushkiApiException("Error tokenizando cuenta Kushki: " + e.getMessage(), e);
        }
    }

    /**
     * Inicia la transferencia bancaria al comercial.
     * Requiere el token obtenido de {@link #tokenizeAccount}.
     */
    public KushkiTransferResponseDTO initiateTransfer(KushkiTransferRequestDTO request) {
        log.info("[KUSHKI] Iniciando transferencia: reference={}, amount={}",
                request.getMerchantTransferReference(),
                request.getAmount() != null ? request.getAmount().getSubtotalIva0() : null);
        try {
            KushkiTransferResponseDTO response = webClient.post()
                    .uri("/payouts/transfer/v1/init")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(KushkiTransferResponseDTO.class)
                    .block();

            if (response == null) {
                throw new KushkiApiException("Kushki no devolvió respuesta para reference="
                        + request.getMerchantTransferReference());
            }

            log.info("[KUSHKI] Transferencia iniciada: transferId={}, code={}, status={}",
                    response.getTransferId(), response.getCode(), response.getStatus());
            return response;

        } catch (WebClientResponseException e) {
            log.error("[KUSHKI] Error iniciando transferencia: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new KushkiApiException("Error iniciando transferencia Kushki: " + e.getMessage(), e);
        }
    }
}
