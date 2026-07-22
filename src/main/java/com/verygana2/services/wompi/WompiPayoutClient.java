package com.verygana2.services.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutBalanceResponseDTO;
import com.verygana2.dtos.wompi.WompiPayoutRequestDTO;
import com.verygana2.dtos.wompi.WompiPayoutResponseDTO;
import com.verygana2.exceptions.wompi.WompiApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente de la API de Pagos a Terceros de Wompi (api.payouts.wompi.co).
 * A diferencia de Kushki, no requiere tokenizar la cuenta destino antes de
 * transferir: una sola llamada a POST /payouts basta.
 */
@Slf4j
@Service
public class WompiPayoutClient {

    private final WebClient webClient;
    private final WompiPayoutConfig wompiPayoutConfig;
    private final ObjectMapper objectMapper;

    public WompiPayoutClient(
            @Qualifier("wompiPayoutWebClient") WebClient webClient,
            WompiPayoutConfig wompiPayoutConfig,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.wompiPayoutConfig = wompiPayoutConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * Consulta el balance disponible en la cuenta de dispersiones configurada.
     */
    public WompiPayoutBalanceResponseDTO.Account getBalance() {
        log.info("[WOMPI PAYOUT] Consultando balance de dispersiones");
        try {
            WompiPayoutBalanceResponseDTO response = webClient.get()
                    .uri("/accounts")
                    .retrieve()
                    .bodyToMono(WompiPayoutBalanceResponseDTO.class)
                    .block();

            List<WompiPayoutBalanceResponseDTO.Account> accounts = response != null ? response.getData() : null;
            if (accounts == null || accounts.isEmpty()) {
                throw new WompiApiException("Wompi no devolvió ninguna cuenta de dispersión", 502);
            }

            return accounts.stream()
                    .filter(acc -> wompiPayoutConfig.getAccountId().equals(acc.getId()))
                    .findFirst()
                    .orElse(accounts.get(0));

        } catch (WebClientResponseException e) {
            log.error("[WOMPI PAYOUT] Error consultando balance: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error consultando balance de Wompi Payouts: " + e.getMessage(),
                    e.getStatusCode().value());
        }
    }

    /**
     * Crea el pago (dispersión) hacia el beneficiario indicado en el request.
     */
    public WompiPayoutResponseDTO createPayout(WompiPayoutRequestDTO request) {
        log.info("[WOMPI PAYOUT] Creando payout: reference={}", request.getReference());
        try {
            // Header obligatorio, único por request (1-64 chars, letras/números/guion,
            // expira en 24h) — no puede ir como header fijo del WebClient.
            // Ref: https://docs.wompi.co/docs/colombia/crea-tu-primer-lote/
            WompiPayoutResponseDTO response = webClient.post()
                    .uri("/payouts")
                    .header("idempotency-key", UUID.randomUUID().toString())
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(WompiPayoutResponseDTO.class)
                    .block();

            if (response == null) {
                throw new WompiApiException(
                        "Wompi no devolvió respuesta para reference=" + request.getReference(), 502);
            }

            log.info("[WOMPI PAYOUT] Payout creado: payoutId={}, status={}", response.getPayoutId(), response.getStatus());
            return response;

        } catch (WebClientResponseException e) {
            log.error("[WOMPI PAYOUT] Error creando payout: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error creando payout en Wompi: " + e.getMessage(),
                    e.getStatusCode().value());
        }
    }

    /**
     * Valida la firma del webhook de Pagos a Terceros.
     * Mismo algoritmo que WompiClient.isValidWebhookSignature (cobros), pero
     * con la eventsKey propia de este producto: SHA256(prop1 + prop2 + ... +
     * timestamp + eventsKey), propiedades listadas en signature.properties.
     */
    @SuppressWarnings("unchecked")
    public boolean isValidWebhookSignature(String rawBody, String checksum) {
        try {
            Map<String, Object> event = objectMapper.readValue(
                    rawBody, new TypeReference<Map<String, Object>>() {});

            Object timestampObj = event.get("timestamp");
            if (timestampObj == null) {
                log.warn("[WOMPI PAYOUT] Webhook sin timestamp — firma no verificable");
                return false;
            }
            String timestamp = timestampObj.toString();

            Map<String, Object> signatureBlock = (Map<String, Object>) event.get("signature");
            if (signatureBlock == null) {
                log.warn("[WOMPI PAYOUT] Webhook sin bloque signature");
                return false;
            }
            List<String> properties = (List<String>) signatureBlock.get("properties");
            if (properties == null || properties.isEmpty()) {
                log.warn("[WOMPI PAYOUT] Webhook con lista de properties vacía");
                return false;
            }

            Map<String, Object> data = (Map<String, Object>) event.get("data");
            Map<String, Object> transaction = (Map<String, Object>) data.get("transaction");

            StringBuilder raw = new StringBuilder();
            for (String property : properties) {
                String field = property.contains(".")
                        ? property.substring(property.lastIndexOf('.') + 1)
                        : property;

                Object value = transaction.get(field);
                if (value == null) {
                    log.warn("[WOMPI PAYOUT] Propiedad '{}' no encontrada en transaction", field);
                    return false;
                }
                raw.append(value);
            }

            raw.append(timestamp);
            raw.append(wompiPayoutConfig.getEventsKey());

            String computed = sha256(raw.toString());
            boolean valid = computed.equals(checksum);

            if (!valid) {
                log.warn("[WOMPI PAYOUT] Firma inválida. computed={}, received={}", computed, checksum);
            }

            return valid;

        } catch (Exception e) {
            log.error("[WOMPI PAYOUT] Error al validar firma del webhook: {}", e.getMessage());
            return false;
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
        }
    }
}
