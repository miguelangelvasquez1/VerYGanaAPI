package com.verygana2.services.wompi;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.verygana2.config.wompi.WompiConfig;
import com.verygana2.dtos.wompi.WompiTransactionRequestDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO;
import com.verygana2.exceptions.wompi.WompiApiException;

import lombok.extern.slf4j.Slf4j;

/**
 * Cliente que encapsula todas las llamadas HTTP a la API de Wompi.
 *
 * RESPONSABILIDAD: comunicación con Wompi únicamente.
 * NO contiene lógica de negocio — eso vive en CopaymentService y PayoutService.
 *
 * Todas las llamadas son síncronas (.block()) porque el resto del sistema
 * es servlet-based (no reactivo). El WebClient se usa por su flexibilidad
 * de configuración, no por reactividad.
 */
@Slf4j
@Service
public class WompiClient {

    private final WebClient webClient;
    private final WompiConfig wompiConfig;

    public WompiClient(
            @Qualifier("wompiWebClient") WebClient webClient,
            WompiConfig wompiConfig) {
        this.webClient = webClient;
        this.wompiConfig = wompiConfig;
    }

    /**
     * Crea una transacción de cobro en Wompi.
     * Usado por CopaymentService para cobrar la parte en dinero real de un copago.
     *
     * @param request datos de la transacción (monto, referencia, método de pago, etc.)
     * @return respuesta de Wompi con el ID de transacción y estado inicial
     * @throws WompiApiException si Wompi rechaza la solicitud o hay error de red
     */
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

    /**
     * Consulta el estado actual de una transacción en Wompi.
     * Usado para reconciliar cuando un webhook no llega o llega duplicado.
     *
     * @param wompiTransactionId ID de la transacción en Wompi
     * @return estado actual de la transacción
     */
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
     * Genera el hash de integridad SHA-256 requerido por Wompi para validar
     * que los parámetros de la transacción no fueron alterados.
     *
     * Fórmula: SHA256(reference + amountInCents + currency + integrityKey)
     *
     * Wompi verifica este hash al procesar la transacción. Si no coincide,
     * la rechaza con error de integridad.
     *
     * @param reference     referencia única de la transacción
     * @param amountInCents monto en centavos de COP
     * @param currency      siempre "COP" en Colombia
     * @return hash SHA-256 en hexadecimal
     */
    public String generateIntegrityHash(String reference, Long amountInCents, String currency) {
        String raw = reference + amountInCents + currency + wompiConfig.getIntegrityKey();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 siempre está disponible en la JVM estándar
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    /**
     * Valida la firma HMAC-SHA256 de un webhook entrante de Wompi.
     * Debe llamarse ANTES de procesar cualquier evento de webhook.
     *
     * Wompi envía el header "x-event-checksum" con el hash del payload.
     * Si la firma no coincide, el webhook debe ignorarse (puede ser fraudulento).
     *
     * Fórmula: SHA256(payload + eventsKey)
     *
     * @param payload   cuerpo del webhook tal como llegó (sin modificar)
     * @param checksum  valor del header "x-event-checksum"
     * @return true si la firma es válida
     */
    public boolean isValidWebhookSignature(String payload, String checksum) {
        String raw = payload + wompiConfig.getEventsKey();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder computed = new StringBuilder();
            for (byte b : hashBytes) {
                computed.append(String.format("%02x", b));
            }
            boolean valid = computed.toString().equals(checksum);
            if (!valid) {
                log.warn("[WOMPI] Firma de webhook inválida. Evento ignorado.");
            }
            return valid;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}