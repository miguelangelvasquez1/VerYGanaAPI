package com.verygana2.services.zapsign;

import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.verygana2.dtos.zapsign.ZapSignCheckResponseDTO;
import com.verygana2.dtos.zapsign.ZapSignCreateCheckRequestDTO;
import com.verygana2.dtos.zapsign.ZapSignCreateDocumentRequestDTO;
import com.verygana2.dtos.zapsign.ZapSignCreateWebhookRequestDTO;
import com.verygana2.dtos.zapsign.ZapSignDocumentResponseDTO;
import com.verygana2.dtos.zapsign.ZapSignWebhookResponseDTO;
import com.verygana2.exceptions.esignature.ESignatureApiException;
import com.verygana2.exceptions.zapsign.ZapSignApiException;

import lombok.extern.slf4j.Slf4j;

/** Cliente delgado de la API de ZapSign (https://docs.zapsign.com.br). */
@Slf4j
@Service
public class ZapSignClient {

    private final WebClient webClient;

    public ZapSignClient(@Qualifier("zapSignWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /** POST /api/v1/docs/ — crea el documento y sus firmantes en una sola llamada. */
    public ZapSignDocumentResponseDTO createDocument(ZapSignCreateDocumentRequestDTO request) {
        log.info("[ZAPSIGN] Creando documento: name={}, externalId={}", request.getName(), request.getExternalId());
        try {
            ZapSignDocumentResponseDTO response = webClient.post()
                    .uri("/api/v1/docs/")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ZapSignDocumentResponseDTO.class)
                    .block();

            if (response == null || response.getToken() == null) {
                throw new ESignatureApiException("ZapSign no devolvió un token de documento para externalId=" + request.getExternalId(), 502);
            }

            log.info("[ZAPSIGN] Documento creado: token={}, status={}", response.getToken(), response.getStatus());
            return response;

        } catch (WebClientResponseException e) {
            log.error("[ZAPSIGN] Error creando documento: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ESignatureApiException(
                    "Error creando documento en ZapSign: " + e.getMessage(), e.getStatusCode().value());
        }
    }

    /** POST /api/v1/checks/ — solicita una consulta de antecedentes (persona o empresa). */
    public ZapSignCheckResponseDTO createCheck(ZapSignCreateCheckRequestDTO request) {
        log.info("[ZAPSIGN] Creando check de antecedentes: type={}, customInput={}",
                request.getType(), request.getCustomInput());
        try {
            ZapSignCheckResponseDTO response = webClient.post()
                    .uri("/api/v1/checks/")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ZapSignCheckResponseDTO.class)
                    .block();

            if (response == null || response.getCheckId() == null) {
                throw new ZapSignApiException(
                        "ZapSign no devolvió un check_id para customInput=" + request.getCustomInput(), 502);
            }

            log.info("[ZAPSIGN] Check creado: checkId={}, status={}", response.getCheckId(), response.getStatus());
            return response;

        } catch (WebClientResponseException e) {
            log.error("[ZAPSIGN] Error creando check: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZapSignApiException(
                    "Error creando check de antecedentes en ZapSign: " + e.getMessage(), e.getStatusCode().value());
        }
    }

    /** GET /api/v1/checks/{check_id}/ — estado y resumen de una consulta de antecedentes. */
    public ZapSignCheckResponseDTO getCheckStatus(String checkId) {
        log.debug("[ZAPSIGN] Consultando estado de check: {}", checkId);
        try {
            return webClient.get()
                    .uri("/api/v1/checks/{checkId}/", checkId)
                    .retrieve()
                    .bodyToMono(ZapSignCheckResponseDTO.class)
                    .block();

        } catch (WebClientResponseException e) {
            log.error("[ZAPSIGN] Error consultando check: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZapSignApiException(
                    "Error consultando check de antecedentes en ZapSign: " + e.getMessage(), e.getStatusCode().value());
        }
    }

    /**
     * GET /api/v1/checks/{check_id}/details/ — hallazgos detallados por fuente. Se devuelve
     * como Map crudo (no un DTO tipado) porque la forma de "tables"/"rows" varía por fuente
     * y país; el frontend la renderiza directamente.
     */
    public Map<String, Object> getCheckDetail(String checkId) {
        log.debug("[ZAPSIGN] Consultando detalle de check: {}", checkId);
        try {
            return webClient.get()
                    .uri("/api/v1/checks/{checkId}/details/", checkId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

        } catch (WebClientResponseException e) {
            log.error("[ZAPSIGN] Error consultando detalle de check: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZapSignApiException(
                    "Error consultando detalle de antecedentes en ZapSign: " + e.getMessage(), e.getStatusCode().value());
        }
    }

    /**
     * POST /api/v1/user/company/webhook/ — registra un webhook a nivel de cuenta. ZapSign solo
     * permite configurar el header de autenticación del webhook por esta vía (no desde su
     * dashboard), por eso este cliente en vez de instrucciones manuales.
     */
    public ZapSignWebhookResponseDTO createWebhook(ZapSignCreateWebhookRequestDTO request) {
        log.info("[ZAPSIGN] Registrando webhook: url={}, type={}", request.getUrl(), request.getType());
        try {
            ZapSignWebhookResponseDTO response = webClient.post()
                    .uri("/api/v1/user/company/webhook/")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ZapSignWebhookResponseDTO.class)
                    .block();

            log.info("[ZAPSIGN] Webhook registrado: id={}, type={}",
                    response != null ? response.getId() : null, request.getType());
            return response;

        } catch (WebClientResponseException e) {
            log.error("[ZAPSIGN] Error registrando webhook: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ZapSignApiException(
                    "Error registrando webhook en ZapSign: " + e.getMessage(), e.getStatusCode().value());
        }
    }
}
