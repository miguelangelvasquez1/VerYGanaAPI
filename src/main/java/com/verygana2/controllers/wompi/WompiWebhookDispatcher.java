package com.verygana2.controllers.wompi;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.verygana2.dtos.wompi.WompiWebhookEvent.WompiTransactionPayload;
import com.verygana2.models.finance.WompiTransaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Enruta cada evento de webhook al servicio de negocio correspondiente.
 *
 * POR QUÉ existe esta clase separada del controller:
 * El controller debe responder 200 a Wompi lo más rápido posible.
 * El procesamiento del copago (actualizar tesorería, debitar llaves, etc.)
 * puede tardar varios cientos de milisegundos.
 * Con @Async el controller responde inmediatamente y el dispatcher
 * procesa en un hilo separado del pool de Spring.
 *
 * POR QUÉ no ir directo a CopaymentService desde el controller:
 * En el futuro habrá más tipos de transacciones (plan básico, plan estándar,
 * depósitos de fortalecimiento). El dispatcher centraliza la lógica de enrutamiento
 * en un solo lugar en lugar de tener un switch gigante en el controller.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WompiWebhookDispatcher {

    // Se inyectarán cuando estén implementados:
    // private final CopaymentService copaymentService;
    // private final PlanSubscriptionService planSubscriptionService;
    // private final BusinessDepositService businessDepositService;

    /**
     * Despacha el evento al servicio correspondiente según el tipo de transacción.
     * Se ejecuta en hilo separado gracias a @Async.
     *
     * Para que @Async funcione necesitas @EnableAsync en tu clase de configuración:
     *   @EnableAsync
     *   @Configuration
     *   public class AsyncConfig { ... }
     */
    @Async
    public void dispatch(WompiTransaction wompiTx, WompiTransactionPayload payload) {
        try {
            log.info("[DISPATCHER] Despachando evento: type={}, reference={}, status={}",
                    wompiTx.getType(), wompiTx.getReference(), wompiTx.getStatus());

            switch (wompiTx.getType()) {

                case CHARGE_COPAYMENT -> handleCopayment(wompiTx, payload);

                case CHARGE_PLAN_SUBSCRIPTION -> handlePlanSubscription(wompiTx, payload);

                case CHARGE_BUSINESS_DEPOSIT -> handleBusinessDeposit(wompiTx, payload);

                default -> log.warn("[DISPATCHER] Tipo de transacción no manejado: {}",
                        wompiTx.getType());
            }

        } catch (Exception e) {
            // Capturamos toda excepción para que el hilo async no muera silenciosamente.
            // El error queda en los logs para investigación manual.
            log.error("[DISPATCHER] Error procesando webhook: type={}, reference={}, error={}",
                    wompiTx.getType(), wompiTx.getReference(), e.getMessage(), e);
        }
    }

    /**
     * Maneja el resultado de un copago (compra de producto con llaves + dinero real).
     * TODO: implementar cuando CopaymentService esté listo (Fase 4).
     */
    private void handleCopayment(WompiTransaction wompiTx, WompiTransactionPayload payload) {
        log.info("[DISPATCHER] Procesando resultado de copago: reference={}, status={}",
                wompiTx.getReference(), wompiTx.getStatus());

        // copaymentService.handleWompiResult(wompiTx);
        //
        // Si APPROVED:
        //   1. Confirmar reserva de llaves en KeyWallet
        //   2. Mover dinero en TreasuryAccount: cashAmount → PAYOUTS_PENDING
        //   3. Mover dinero en TreasuryAccount: keysValue de KEYS_RESERVE → PAYOUTS_PENDING
        //   4. Asignar ProductStock al PurchaseItem
        //   5. Pasar Copayment y Purchase a COMPLETED
        //   6. Enviar email de entrega del producto digital
        //
        // Si DECLINED / ERROR:
        //   1. Liberar llaves reservadas en KeyWallet
        //   2. Pasar Copayment a FAILED
        //   3. Notificar al usuario

        log.warn("[DISPATCHER] handleCopayment pendiente de implementar (Fase 4)");
    }

    /**
     * Maneja el pago del plan básico mensual.
     * TODO: implementar cuando PlanSubscriptionService esté listo (Fase 3).
     */
    private void handlePlanSubscription(WompiTransaction wompiTx, WompiTransactionPayload payload) {
        log.info("[DISPATCHER] Procesando pago de suscripción: reference={}, status={}",
                wompiTx.getReference(), wompiTx.getStatus());

        // planSubscriptionService.handleWompiResult(wompiTx);
        //
        // Si APPROVED:
        //   1. Activar o renovar la suscripción del empresario
        //   2. Distribuir en tesorería: 60% KEYS_RESERVE, 10% FORTIFICATION, 30% OPERATIONS
        //   3. Distribuir llaves a usuarios según interacciones activas

        log.warn("[DISPATCHER] handlePlanSubscription pendiente de implementar (Fase 3)");
    }

    /**
     * Maneja el depósito inicial o recarga de plan estándar/premium.
     * TODO: implementar cuando BusinessDepositService esté listo (Fase 3).
     */
    private void handleBusinessDeposit(WompiTransaction wompiTx, WompiTransactionPayload payload) {
        log.info("[DISPATCHER] Procesando depósito empresarial: reference={}, status={}",
                wompiTx.getReference(), wompiTx.getStatus());

        // businessDepositService.handleWompiResult(wompiTx);
        //
        // Si APPROVED:
        //   1. Crear o recargar Investment del empresario
        //   2. Distribuir en tesorería: 60% KEYS_RESERVE, 10% FORTIFICATION, 30% OPERATIONS
        //   3. Distribuir llaves a usuarios

        log.warn("[DISPATCHER] handleBusinessDeposit pendiente de implementar (Fase 3)");
    }
}
