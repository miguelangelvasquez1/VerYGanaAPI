package com.verygana2.controllers.wompi;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.wompi.WompiWebhookEvent.WompiTransactionPayload;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.services.interfaces.finance.PayoutService;
import com.verygana2.services.interfaces.finance.PlanService;
import com.verygana2.services.interfaces.marketplace.CopaymentService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests de {@link WompiWebhookDispatcher}: enruta cada {@link WompiTransaction}
 * al servicio de negocio correspondiente según su {@link WompiTransactionType},
 * pasando solo el UUID (no la entidad) porque corre en un hilo @Async separado.
 * Se mockean los tres servicios destino; {@code dispatch} se invoca
 * directamente (no a través del executor @Async) para probar la lógica de
 * enrutamiento de forma síncrona.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WompiWebhookDispatcher")
class WompiWebhookDispatcherTest {

    @Mock private PlanService planService;
    @Mock private CopaymentService copaymentService;
    @Mock private PayoutService payoutService;

    private WompiWebhookDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new WompiWebhookDispatcher(planService, copaymentService, payoutService);
    }

    private WompiTransaction transaction(WompiTransactionType type) {
        UUID id = UUID.randomUUID();
        return WompiTransaction.builder()
                .id(id)
                .wompiId("wompi-1")
                .type(type)
                .amountInCents(100000L)
                .currency("COP")
                .status(WompiTransactionStatus.APPROVED)
                .reference("VG-REF-1")
                .build();
    }

    @Test
    @DisplayName("CHARGE_PLAN_SUBSCRIPTION despacha a PlanService.handleWompiResult con el id de la transacción")
    void chargePlanSubscription_dispatchesToPlanService() {
        WompiTransaction tx = transaction(WompiTransactionType.CHARGE_PLAN_SUBSCRIPTION);

        dispatcher.dispatch(tx, null);

        verify(planService).handleWompiResult(tx.getId());
        verifyNoInteractions(copaymentService, payoutService);
    }

    @Test
    @DisplayName("CHARGE_BUSINESS_DEPOSIT también despacha a PlanService.handleWompiResult (mismo handler que CHARGE_PLAN_SUBSCRIPTION)")
    void chargeBusinessDeposit_dispatchesToPlanService() {
        WompiTransaction tx = transaction(WompiTransactionType.CHARGE_BUSINESS_DEPOSIT);

        dispatcher.dispatch(tx, null);

        verify(planService).handleWompiResult(tx.getId());
        verifyNoInteractions(copaymentService, payoutService);
    }

    @Test
    @DisplayName("CHARGE_COPAYMENT despacha a CopaymentService.handleWompiResult")
    void chargeCopayment_dispatchesToCopaymentService() {
        WompiTransaction tx = transaction(WompiTransactionType.CHARGE_COPAYMENT);

        dispatcher.dispatch(tx, null);

        verify(copaymentService).handleWompiResult(tx.getId());
        verifyNoInteractions(planService, payoutService);
    }

    @Test
    @DisplayName("TRANSFER_PAYOUT despacha a PayoutService.handleWompiResult")
    void transferPayout_dispatchesToPayoutService() {
        WompiTransaction tx = transaction(WompiTransactionType.TRANSFER_PAYOUT);

        dispatcher.dispatch(tx, null);

        verify(payoutService).handleWompiResult(tx.getId());
        verifyNoInteractions(planService, copaymentService);
    }

    @Test
    @DisplayName("payload de webhook se acepta pero no se usa para decidir el enrutamiento (solo el tipo de la transacción importa)")
    void payloadIsIgnoredForRouting_onlyTransactionTypeMatters() {
        WompiTransaction tx = transaction(WompiTransactionType.CHARGE_COPAYMENT);
        WompiTransactionPayload payload = new WompiTransactionPayload();

        dispatcher.dispatch(tx, payload);

        verify(copaymentService).handleWompiResult(tx.getId());
    }

    @Test
    @DisplayName("una excepción lanzada por el servicio destino se captura dentro de dispatch y no se propaga")
    void exceptionFromTargetService_isCaughtAndDoesNotPropagate() {
        WompiTransaction tx = transaction(WompiTransactionType.CHARGE_COPAYMENT);
        doThrow(new RuntimeException("boom")).when(copaymentService).handleWompiResult(any());

        dispatcher.dispatch(tx, null);

        verify(copaymentService).handleWompiResult(tx.getId());
        verifyNoInteractions(planService, payoutService);
    }

    // Nota: WompiTransactionType solo tiene 4 constantes y las 4 están cubiertas
    // arriba (CHARGE_PLAN_SUBSCRIPTION, CHARGE_BUSINESS_DEPOSIT, CHARGE_COPAYMENT,
    // TRANSFER_PAYOUT), así que el branch `default -> log.warn(...)` del switch es
    // inalcanzable con el enum actual — existe solo como red de seguridad si se agrega
    // un tipo nuevo sin actualizar el switch. No es ejercitable sin modificar el enum
    // de producción, así que no se agrega un test dedicado para ese branch.
}
