package com.verygana2.services.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutBalanceResponseDTO;
import com.verygana2.dtos.wompi.WompiPayoutBankResponseDTO;
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

        } catch (WebClientResponseException.TooManyRequests e) {
            log.error("[WOMPI PAYOUT] Wompi rate-limited (429) consultando balance: {}", e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Wompi rechazó por límite de tasa (429) consultando balance — no es un rechazo de negocio, reintentar más tarde: "
                            + e.getMessage(),
                    429);
        } catch (WebClientResponseException e) {
            log.error("[WOMPI PAYOUT] Error consultando balance: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error consultando balance de Wompi Payouts: " + e.getMessage(),
                    e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            log.error("[WOMPI PAYOUT] Timeout/error de red consultando balance: {}", e.getMessage());
            throw new WompiApiException(
                    "Timeout o error de red consultando balance de Wompi Payouts: " + e.getMessage(), 504);
        }
    }

    /**
     * Consulta el catálogo de bancos/canales disponibles para Pagos a Terceros.
     * Se usa para validar que el bankCode que registra un commercial para
     * BANK_TRANSFER exista realmente en el catálogo de Wompi antes de aceptarlo.
     */
    public List<WompiPayoutBankResponseDTO.Bank> getBanks() {
        log.info("[WOMPI PAYOUT] Consultando catálogo de bancos");
        try {
            WompiPayoutBankResponseDTO response = webClient.get()
                    .uri("/banks")
                    .retrieve()
                    .bodyToMono(WompiPayoutBankResponseDTO.class)
                    .block();

            List<WompiPayoutBankResponseDTO.Bank> banks = response != null ? response.getData() : null;
            if (banks == null) {
                throw new WompiApiException("Wompi no devolvió el catálogo de bancos", 502);
            }
            return banks;

        } catch (WebClientResponseException.TooManyRequests e) {
            log.error("[WOMPI PAYOUT] Wompi rate-limited (429) consultando catálogo de bancos: {}", e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Wompi rechazó por límite de tasa (429) consultando catálogo de bancos — no es un rechazo de negocio, reintentar más tarde: "
                            + e.getMessage(),
                    429);
        } catch (WebClientResponseException e) {
            log.error("[WOMPI PAYOUT] Error consultando catálogo de bancos: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error consultando catálogo de bancos de Wompi Payouts: " + e.getMessage(),
                    e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            log.error("[WOMPI PAYOUT] Timeout/error de red consultando catálogo de bancos: {}", e.getMessage());
            throw new WompiApiException(
                    "Timeout o error de red consultando catálogo de bancos de Wompi Payouts: " + e.getMessage(), 504);
        }
    }

    /**
     * Crea el pago (dispersión) hacia el beneficiario indicado en el request.
     */
    public WompiPayoutResponseDTO createPayout(WompiPayoutRequestDTO request) {
        log.info("[WOMPI PAYOUT] Creando payout: reference={}", request.getReference());
        try {
            // Header obligatorio (1-64 chars, letras/números/guion, expira en 24h) — no
            // puede ir como header fijo del WebClient.
            // Ref: https://docs.wompi.co/docs/colombia/crea-tu-primer-lote/
            //
            // A propósito NO es un UUID nuevo por request: se deriva de request.getReference()
            // (= payout.getId(), estable entre reintentos). Si Wompi ya ejecutó la
            // transferencia pero nuestro guardado posterior falla (ej. la BD se cae justo
            // después del 201), el Payout queda FAILED/SCHEDULED localmente aunque el dinero
            // ya salió. Con una key nueva por intento, el siguiente reintento sería una
            // transferencia nueva para Wompi → pago duplicado. Con la misma key, Wompi la
            // reconoce como duplicado y devuelve el resultado de la transferencia original
            // en vez de ejecutarla de nuevo.
            WompiPayoutResponseDTO response = webClient.post()
                    .uri("/payouts")
                    .header("idempotency-key", request.getReference())
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

        } catch (WebClientResponseException.TooManyRequests e) {
            // Distinto de un rechazo real (ej. cuenta bancaria inválida): esto es Wompi
            // limitando el volumen de requests, no un problema con este payout en particular.
            // Sin esta distinción, quedaría marcado FAILED con el mismo motivo genérico que
            // cualquier otro rechazo, mezclando "hubo que frenar por volumen" con "el dato
            // estaba mal" — confuso para quien revise el panel de admin.
            log.error("[WOMPI PAYOUT] Wompi rate-limited (429) creando payout reference={}: {}",
                    request.getReference(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Wompi rechazó por límite de tasa (429 Too Many Requests) — no es un rechazo de la transferencia, reintentar más tarde: "
                            + e.getMessage(),
                    429);
        } catch (WebClientResponseException e) {
            log.error("[WOMPI PAYOUT] Error creando payout: status={}, body={}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error creando payout en Wompi: " + e.getMessage(),
                    e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            // Wompi no respondió a tiempo (o la conexión ni se estableció) — no sabemos
            // si la transferencia sí se ejecutó del otro lado. El caller marca el payout
            // como FAILED igual, quedando disponible para un reintento manual/nocturno.
            log.error("[WOMPI PAYOUT] Timeout/error de red creando payout reference={}: {}",
                    request.getReference(), e.getMessage());
            throw new WompiApiException(
                    "Timeout o error de red creando payout en Wompi: " + e.getMessage(), 504);
        }
    }

    /**
     * Consulta directamente en Wompi el estado real de un payout ya creado —
     * útil para diagnóstico cuando el webhook de confirmación no llega o no
     * correlaciona (independiente de nuestro webhook, va directo a la fuente).
     * Se devuelve el body crudo tal cual lo responde Wompi: no sabemos aún el
     * shape exacto de este endpoint en la práctica (el spec público no
     * siempre es confiable), así que no forzamos un DTO todavía.
     */
    public Map<String, Object> getPayoutStatus(String payoutId) {
        log.info("[WOMPI PAYOUT] Consultando estado de payout: {}", payoutId);
        try {
            return webClient.get()
                    .uri("/payouts/{id}", payoutId)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block();

        } catch (WebClientResponseException.TooManyRequests e) {
            log.error("[WOMPI PAYOUT] Wompi rate-limited (429) consultando estado de payout {}: {}",
                    payoutId, e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Wompi rechazó por límite de tasa (429) consultando estado de payout — no es un rechazo de negocio, reintentar más tarde: "
                            + e.getMessage(),
                    429);
        } catch (WebClientResponseException e) {
            log.error("[WOMPI PAYOUT] Error consultando estado de payout {}: status={}, body={}",
                    payoutId, e.getStatusCode(), e.getResponseBodyAsString());
            throw new WompiApiException(
                    "Error consultando estado de payout en Wompi: " + e.getMessage(),
                    e.getStatusCode().value());
        } catch (WebClientRequestException e) {
            log.error("[WOMPI PAYOUT] Timeout/error de red consultando estado de payout {}: {}",
                    payoutId, e.getMessage());
            throw new WompiApiException(
                    "Timeout o error de red consultando estado de payout en Wompi: " + e.getMessage(), 504);
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
            Map<String, Object> transaction = data != null ? (Map<String, Object>) data.get("transaction") : null;

            if (transaction == null) {
                // Evento "payout.updated" (nivel de lote, trae data.payout en vez de
                // data.transaction) u otro tipo no soportado — no es un error, solo
                // no aplica esta validación. Lo ignoramos sin firmar; el controller
                // ya descarta estos eventos por tipo antes de procesar nada.
                log.debug("[WOMPI PAYOUT] Webhook sin data.transaction (probablemente payout.updated) — se ignora");
                return false;
            }

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
