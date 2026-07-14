package com.verygana2.services.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.wompi.WompiConfig;
import com.verygana2.dtos.wompi.WompiTransactionListResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionRequestDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO;
import com.verygana2.exceptions.wompi.WompiApiException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WompiClient {

    private final WebClient webClient;
    private final WompiConfig wompiConfig;
    private final ObjectMapper objectMapper;

    public WompiClient(
            @Qualifier("wompiWebClient") WebClient webClient,
            WompiConfig wompiConfig,
            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.wompiConfig = wompiConfig;
        this.objectMapper = objectMapper;
    }

    public WompiTransactionResponseDTO createTransaction(WompiTransactionRequestDTO request) {
        log.info("[WOMPI] Creando transacción: reference={}, amount={}",
                request.getReference(), request.getAmountInCents());
        try {
            WompiTransactionResponseDTO response = webClient.post()
                    .uri("/transactions")
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class)
                    .block();

            log.info("[WOMPI] Transacción creada: wompiId={}, status={}",
                    response != null ? response.getData().getId() : "null",
                    response != null ? response.getData().getStatus() : "null");

            return response;

        } catch (WebClientResponseException e) {
            log.error("[WOMPI] Error al crear transacción: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error al crear transacción en Wompi: " + e.getMessage(),
                    e.getStatusCode().value());
        }
    }

    public WompiTransactionResponseDTO getTransaction(String wompiTransactionId) {
        log.debug("[WOMPI] Consultando transacción: {}", wompiTransactionId);
        try {
            return webClient.get()
                    .uri("/transactions/{id}", wompiTransactionId)
                    .retrieve()
                    .bodyToMono(WompiTransactionResponseDTO.class)
                    .block();

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new WompiApiException(
                        "Transacción no encontrada en Wompi: " + wompiTransactionId, 404);
            }
            throw new WompiApiException(
                    "Error al consultar transacción en Wompi: " + e.getMessage(),
                    e.getStatusCode().value());
        }
    }

    /**
     * Busca transacciones en Wompi por la referencia interna que enviamos al crear
     * el checkout. A diferencia de {@link #getTransaction}, no necesita el ID real
     * de Wompi — sirve para reconciliar pagos cuyo webhook nunca llegó.
     *
     * @return la transacción más reciente para esa referencia, o null si Wompi no
     *         tiene ningún registro (el usuario nunca completó el checkout)
     */
    public WompiTransactionResponseDTO.WompiTransactionData findTransactionByReference(String reference) {
        log.debug("[WOMPI] Buscando transacción por reference: {}", reference);
        try {
            WompiTransactionListResponseDTO response = webClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/transactions").queryParam("reference", reference).build())
                    .retrieve()
                    .bodyToMono(WompiTransactionListResponseDTO.class)
                    .block();

            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                return null;
            }

            // Reintentos del usuario pueden dejar varias transacciones con la misma
            // referencia; nos quedamos con la más reciente.
            return response.getData().stream()
                    .max(Comparator.comparing(WompiTransactionResponseDTO.WompiTransactionData::getCreatedAt))
                    .orElse(null);

        } catch (WebClientResponseException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return null;
            }
            throw new WompiApiException(
                    "Error al buscar transacción por reference en Wompi: " + e.getMessage(),
                    e.getStatusCode().value());
        }
    }

    /**
     * Genera el hash de integridad SHA-256 para firmar el checkout.
     * Fórmula: SHA256(reference + amountInCents + currency + integrityKey)
     */
    public String generateIntegrityHash(String reference, Long amountInCents, String currency) {
        String raw = reference + amountInCents + currency + wompiConfig.getIntegritySecret();
        log.info("[WOMPI HASH] raw string = '{}'", raw); // ← agregar temporalmente
        log.info("[WOMPI HASH] integritySecret = '{}'", wompiConfig.getIntegritySecret()); // ← agregar
        return sha256(raw);
    }

    /**
     * Valida la firma del webhook entrante de Wompi.
     *
     * Fórmula CORRECTA según docs.wompi.co/docs/colombia/eventos:
     * SHA256(prop1Value + prop2Value + ... + timestamp + eventsKey)
     *
     * Las propiedades a concatenar vienen en event.signature.properties.
     * Los valores se extraen del objeto event.data.transaction.
     * Al final se concatena event.timestamp y la eventsKey de tu cuenta.
     *
     * Ejemplo con properties =
     * ["transaction.id","transaction.status","transaction.amount_in_cents"]:
     * raw = "abc-123" + "APPROVED" + "300000" + "1668097749" + eventsKey
     */
    @SuppressWarnings("unchecked")
    public boolean isValidWebhookSignature(String rawBody, String checksum) {
        try {
            Map<String, Object> event = objectMapper.readValue(
                    rawBody, new TypeReference<Map<String, Object>>() {
                    });

            Object timestampObj = event.get("timestamp");
            if (timestampObj == null) {
                log.warn("[WOMPI] Webhook sin timestamp — firma no verificable");
                return false;
            }
            String timestamp = timestampObj.toString();

            Map<String, Object> signatureBlock = (Map<String, Object>) event.get("signature");
            if (signatureBlock == null) {
                log.warn("[WOMPI] Webhook sin bloque signature");
                return false;
            }
            List<String> properties = (List<String>) signatureBlock.get("properties");
            if (properties == null || properties.isEmpty()) {
                log.warn("[WOMPI] Webhook con lista de properties vacía");
                return false;
            }

            Map<String, Object> data = (Map<String, Object>) event.get("data");
            Map<String, Object> transaction = (Map<String, Object>) data.get("transaction");

            StringBuilder raw = new StringBuilder();
            for (String property : properties) {
                // property tiene formato "transaction.campo" → extraemos el campo
                String field = property.contains(".")
                        ? property.substring(property.lastIndexOf('.') + 1)
                        : property;

                Object value = transaction.get(field);
                if (value == null) {
                    log.warn("[WOMPI] Propiedad '{}' no encontrada en transaction", field);
                    return false;
                }
                raw.append(value);
            }

            raw.append(timestamp);
            raw.append(wompiConfig.getEventsKey());

            String computed = sha256(raw.toString());
            boolean valid = computed.equals(checksum);

            if (!valid) {
                log.warn("[WOMPI] Firma inválida. computed={}, received={}", computed, checksum);
            }

            return valid;

        } catch (Exception e) {
            log.error("[WOMPI] Error al validar firma del webhook: {}", e.getMessage());
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