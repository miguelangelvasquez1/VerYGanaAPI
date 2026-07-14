package com.verygana2.services.wompi;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import com.verygana2.config.wompi.WompiConfig;
import com.verygana2.dtos.wompi.WompiCheckoutRequestDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.dtos.wompi.WompiTransactionResponseDTO.WompiTransactionData;
import com.verygana2.exceptions.wompi.WompiApiException;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.repositories.finance.WompiTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Servicio que orquesta las operaciones con Wompi desde la perspectiva
 * del negocio de VeryGana.
 *
 * RESPONSABILIDADES:
 *   - Construir la URL del checkout firmada para redirigir al usuario
 *   - Consultar el estado de una transacción en Wompi
 *   - Persistir y actualizar registros WompiTransaction
 *
 * NO es responsable de:
 *   - Lógica de copagos (eso es CopaymentService)
 *   - Lógica de payouts (eso es PayoutService)
 *   - Validación de webhooks (eso es WompiWebhookController)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WompiService {

    private final WompiClient wompiClient;
    private final WompiConfig wompiConfig;
    private final WompiTransactionRepository wompiTransactionRepository;

    /**
     * Genera la URL del Checkout de Wompi para que el frontend redirija al usuario.
     *
     * Con el flujo de Checkout NO se hace ninguna llamada HTTP a Wompi en este paso.
     * El backend solo construye y firma la URL. Wompi crea la transacción internamente
     * cuando el usuario llega a esa página y completa el pago.
     *
     * Flujo posterior:
     *   1. Frontend redirige al usuario a checkoutUrl
     *   2. Usuario paga en la página de Wompi (todos los métodos disponibles)
     *   3. Wompi notifica el resultado via webhook → WompiWebhookController
     *   4. WompiWebhookController llama a updateTransactionFromWebhook()
     *
     * @param request  parámetros del checkout (referencia, monto, email, redirectUrl)
     * @param type     tipo de transacción para clasificar el registro interno
     * @return URL del checkout lista para redirigir + referencia para tracking
     */
    @Transactional
    public WompiCheckoutResponseDTO createCheckoutUrl(
            WompiCheckoutRequestDTO request,
            WompiTransactionType type) {

        log.info("[WOMPI SERVICE] Generating checkout URL: reference={}, amount={}",
                request.getReference(), request.getAmountInCents());

        // 1. Generar hash de integridad SHA-256
        //    Fórmula: SHA256(reference + amountInCents + currency + integrityKey)
        String integrityHash = wompiClient.generateIntegrityHash(
                request.getReference(),
                request.getAmountInCents(),
                "COP"
        );

        // 2. Construir la URL del checkout con todos los parámetros requeridos por Wompi
        String checkoutUrl = UriComponentsBuilder
                .fromUriString(wompiConfig.getCheckoutBaseUrl())
                .queryParam("public-key",          wompiConfig.getPublicKey())
                .queryParam("currency",            "COP")
                .queryParam("amount-in-cents",     request.getAmountInCents())
                .queryParam("reference",           request.getReference())
                .queryParam("signature:integrity", integrityHash)
                .queryParam("redirect-url",        request.getRedirectUrl())
                .queryParam("customer-data:email", request.getCustomerEmail())
                .build()
                .toUriString();

        log.debug("[WOMPI SERVICE] Checkout URL generated for reference={}", request.getReference());

        // 3. Persistir un WompiTransaction en estado PENDING como registro anticipado.
        //    Esto es importante: si el servidor cae entre que generamos la URL y que
        //    llega el webhook, tenemos el registro para reconciliar manualmente.
        //    wompiId queda vacío — se llenará cuando llegue el webhook con el ID real.
        WompiTransaction pendingRecord = WompiTransaction.builder()
                .wompiId("PENDING-" + request.getReference())
                .type(type)
                .amountInCents(request.getAmountInCents())
                .currency("COP")
                .status(WompiTransactionStatus.PENDING)
                .reference(request.getReference())
                .metadata(Map.of(
                        "checkout_url_generated_at",
                        ZonedDateTime.now(ZoneOffset.UTC).toString(),
                        "customer_email",
                        request.getCustomerEmail() != null ? request.getCustomerEmail() : ""
                ))
                .build();

        wompiTransactionRepository.save(pendingRecord);
        log.info("[WOMPI SERVICE] WompiTransaction pre-registered: reference={}", request.getReference());

        return WompiCheckoutResponseDTO.builder()
                .checkoutUrl(checkoutUrl)
                .reference(request.getReference())
                .amountInCents(request.getAmountInCents())
                .build();
    }

     /**
     * Verifica si una transacción de Wompi ya fue procesada.
     * Evita el doble procesamiento cuando Wompi reenvía el mismo webhook.
     *
     * Una transacción está "procesada" cuando:
     *   - Ya existe en la BD con ese wompiId real (no el "PENDING-" provisional)
     *   - Y su status no es PENDING
     *
     * @param wompiId ID real de la transacción en Wompi
     * @return true si ya fue procesada y no debe volver a procesarse
     */
    public boolean isAlreadyProcessed(String wompiId) {
        return wompiTransactionRepository.findByWompiId(wompiId)
                .map(tx -> tx.getStatus() != WompiTransactionStatus.PENDING)
                .orElse(false);
    }

    /**
     * Reconcilia una transacción directamente contra la API de Wompi por su
     * referencia interna, sin depender de que el webhook haya llegado (a diferencia
     * de consultar por el wompiId real, que todavía no existe si el webhook nunca
     * notificó). Usado por el scheduler de expiración de compras antes de dar por
     * abandonado un copago PENDING, y sirve también para reconciliación manual.
     *
     * @param reference referencia interna de la compra (Purchase.referenceId)
     * @return los datos de Wompi si existe alguna transacción para esa referencia,
     *         o empty si Wompi no tiene ningún registro (el usuario nunca llegó a pagar)
     * @throws WompiApiException si hay un error de red o de la API de Wompi
     */
    public Optional<WompiTransactionData> reconcileByReference(String reference) {
        log.info("[WOMPI SERVICE] Reconciliando contra Wompi: reference={}", reference);
        WompiTransactionData data = wompiClient.findTransactionByReference(reference);
        if (data == null) {
            log.info("[WOMPI SERVICE] Wompi no tiene ninguna transacción registrada para reference={}", reference);
            return Optional.empty();
        }
        return Optional.of(data);
    }

    /**
     * Actualiza un WompiTransaction local con los datos que llegaron en un webhook.
     * Llamado por WompiWebhookController después de validar la firma del webhook.
     *
     * Este método:
     *   1. Busca el registro local por la referencia del webhook
     *   2. Actualiza wompiId, status y metadata con el payload completo
     *   3. Persiste los cambios
     *
     * @param wompiId        ID real de la transacción en Wompi
     * @param reference      referencia interna que enviamos al crear el checkout
     * @param status         nuevo estado ("APPROVED", "DECLINED", "ERROR", "VOIDED")
     * @param wompiCreatedAt timestamp ISO-8601 del campo "created_at" del payload
     *                       de Wompi (momento en que Wompi procesó la transacción)
     * @param metadata       payload completo del webhook para auditoría
     * @return WompiTransaction actualizada
     */
    @Transactional
    public WompiTransaction updateTransactionFromWebhook(
            String wompiId,
            String reference,
            String status,
            String wompiCreatedAt,
            Map<String, Object> metadata) {

        log.info("[WOMPI SERVICE] updating transaction por webhook: " +
                "wompiId={}, reference={}, status={}", wompiId, reference, status);

        WompiTransaction transaction = wompiTransactionRepository
                .findByReference(reference)
                .orElseThrow(() -> {
                    log.error("[WOMPI SERVICE] Webhook received for unknown reference: {}", reference);
                    return new IllegalArgumentException(
                            "WompiTransaction with reference: " + reference + " not found ");
                });

        // Actualizar con los datos reales de Wompi
        transaction.setWompiId(wompiId);
        transaction.setStatus(parseStatus(status));
        transaction.setMetadata(metadata);
        transaction.setUpdatedAt(ZonedDateTime.now(ZoneOffset.UTC));

        if ("APPROVED".equals(status)) {
            transaction.setWompiCreatedAt(parseWompiCreatedAt(wompiCreatedAt));
        }

        WompiTransaction saved = wompiTransactionRepository.save(transaction);
        log.info("[WOMPI SERVICE] WompiTransaction updated: reference={}, status={}",
                reference, status);

        return saved;
    }

    /**
     * Parsea el "created_at" que envía Wompi en el payload del webhook.
     * Es dato externo: si Wompi cambia el formato o no lo envía, no debe
     * tumbar el procesamiento del webhook — se loguea y se deja null.
     */
    private ZonedDateTime parseWompiCreatedAt(String wompiCreatedAt) {
        if (wompiCreatedAt == null || wompiCreatedAt.isBlank()) {
            return null;
        }

        try {
            return ZonedDateTime.parse(wompiCreatedAt);
        } catch (DateTimeParseException e) {
            log.warn("[WOMPI SERVICE] No se pudo parsear wompi created_at='{}': {}",
                    wompiCreatedAt, e.getMessage());
            return null;
        }
    }

    /**
     * Convierte el string de status de Wompi al enum interno.
     * Si Wompi envía un status desconocido, se trata como ERROR
     * y se loguea para investigación.
     */
    private WompiTransactionStatus parseStatus(String wompiStatus) {
        return switch (wompiStatus.toUpperCase()) {
            case "APPROVED" -> WompiTransactionStatus.APPROVED;
            case "DECLINED" -> WompiTransactionStatus.DECLINED;
            case "VOIDED"   -> WompiTransactionStatus.VOIDED;
            case "ERROR"    -> WompiTransactionStatus.ERROR;
            default -> {
                log.warn("[WOMPI SERVICE] Unknown status received from Wompi: {}", wompiStatus);
                yield WompiTransactionStatus.ERROR;
            }
        };
    }
}
