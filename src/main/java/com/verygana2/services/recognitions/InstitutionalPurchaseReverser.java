package com.verygana2.services.recognitions;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.ComputableSaleStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.enums.recognitions.RecognitionStatus;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.recognitions.Recognition;
import com.verygana2.repositories.finance.PayoutItemRepository;
import com.verygana2.repositories.recognitions.RecognitionRepository;
import com.verygana2.services.interfaces.finance.ProsperityService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.marketplace.SaleComponentRecorder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Deshace la venta de un ítem comprado por VERYGANA (Contrato B 10.16 G): el Saldo de
 * Prosperidad recupera lo absorbido, los componentes reciben su asiento compensatorio y el
 * dinero vuelve al fondo de fortalecimiento, nunca al beneficiario. El registro original
 * (compra, ítem, componentes) se conserva.
 *
 * No toca el estado del ítem ni el stock: eso lo decide quien llama (vencimiento o anulación).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InstitutionalPurchaseReverser {

    private final ProsperityService prosperityService;
    private final SaleComponentRecorder saleComponentRecorder;
    private final TreasuryService treasuryService;
    private final RecognitionRepository recognitionRepository;
    private final PayoutItemRepository payoutItemRepository;

    /**
     * @return false si el ítem ya estaba reversado (idempotente); true si se reversó ahora.
     * @throws InvalidStatusException si el ítem ya entró a un payout: el dinero ya salió hacia
     *         el comercio y no se puede devolver al fondo desde aquí.
     */
    public boolean reverse(PurchaseItem item, String cause) {
        if (item.getComputableStatus() == ComputableSaleStatus.REVERSED) {
            return false;
        }
        if (payoutItemRepository.existsByPurchaseItemId(item.getId())) {
            throw new InvalidStatusException("El ítem " + item.getId() + " ya fue pagado al comercio");
        }

        Recognition recognition = recognitionRepository.findByPurchaseId(item.getPurchase().getId())
                .orElseThrow(() -> new IllegalStateException(
                        "[RECOGNITION] Compra de VERYGANA sin reconocimiento: purchaseId=" + item.getPurchase().getId()));

        // Mismo orden de bloqueo que la compra: prosperidad → tesorería.
        prosperityService.reintegrateRefund(item);
        saleComponentRecorder.reverse(item, cause);
        treasuryService.reverseInstitutionalPurchaseItem(item.getSubtotalCents(), item.getCommissionCents(),
                item.getCommissionVatCents(), recognition.getFundingRef());

        log.info("[RECOGNITION] Ítem de reconocimiento reversado: recognition={}, itemId={}, causa={}",
                recognition.getPublicId(), item.getId(), cause);
        return true;
    }

    /**
     * Recalcula el estado del reconocimiento después de que uno de sus ítems se reclamó o
     * venció: con ítems aún por reclamar sigue PURCHASED; si alguno se entregó queda
     * DELIVERED; si ninguno llegó al beneficiario, EXPIRED.
     */
    public void settleStatus(Recognition recognition) {
        if (recognition.getStatus() != RecognitionStatus.PURCHASED) {
            return;
        }
        long pending = recognition.getPurchase().getItems().stream()
                .filter(i -> i.getStatus() == PurchaseItemStatus.PENDING).count();
        if (pending > 0) {
            return;
        }
        boolean anyDelivered = recognition.getPurchase().getItems().stream()
                .anyMatch(i -> i.getStatus() == PurchaseItemStatus.CLAIMED);
        if (anyDelivered) {
            recognition.markDelivered();
        } else {
            recognition.markExpired();
        }
        log.info("[RECOGNITION] Reconocimiento {} cerrado como {} a las {}", recognition.getPublicId(),
                recognition.getStatus(), ZonedDateTime.now(ZoneOffset.UTC));
    }
}
