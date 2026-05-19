package com.verygana2.controllers.wompi;

import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.verygana2.dtos.wompi.WompiWebhookEvent.WompiTransactionPayload;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.services.interfaces.finance.PlanService;
import com.verygana2.services.interfaces.marketplace.CopaymentService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enruta cada evento de webhook al servicio de negocio correspondiente.
 *
 * FIX CRÍTICO — por qué pasamos el ID y no la entidad:
 * El dispatcher corre en un hilo @Async separado. La entidad WompiTransaction
 * fue cargada en la transacción del hilo principal (WompiWebhookController),
 * que ya cerró cuando el hilo async empieza. Si se pasa la entidad directamente,
 * cualquier acceso a una relación LAZY lanza LazyInitializationException y la
 * transacción del dispatcher no puede hacer commit.
 *
 * Solución: pasar solo el UUID del WompiTransaction. Cada servicio lo recarga
 * dentro de su propia transacción @Transactional con todas sus relaciones.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WompiWebhookDispatcher {

    private final PlanService planService;
    private final CopaymentService copaymentService;

    @Async
    public void dispatch(WompiTransaction wompiTx, WompiTransactionPayload payload) {

        // Extraer solo los datos primitivos ANTES de salir del hilo principal
        UUID wompiTxId = wompiTx.getId();
        WompiTransactionType type = wompiTx.getType();
        String reference = wompiTx.getReference();

        try {
            log.info("[DISPATCHER] Despachando: type={}, reference={}, wompiTxId={}",
                    type, reference, wompiTxId);

            switch (type) {

                case CHARGE_PLAN_SUBSCRIPTION,
                     CHARGE_BUSINESS_DEPOSIT -> planService.handleWompiResult(wompiTxId);

                case CHARGE_COPAYMENT -> copaymentService.handleWompiResult(wompiTxId);

                case TRANSFER_PAYOUT -> {
                    log.info("[DISPATCHER] TRANSFER_PAYOUT recibido: reference={}", reference);
                    // payoutService.handleWompiResult(wompiTxId); // Fase 5
                }

                default -> log.warn("[DISPATCHER] Tipo no manejado: {}", type);
            }

        } catch (Exception e) {
            log.error("[DISPATCHER] Error procesando webhook: type={}, reference={}, error={}",
                    type, reference, e.getMessage(), e);
        }
    }
}