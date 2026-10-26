package com.verygana2.services.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.marketplace.ComputableSaleStatus;
import com.verygana2.models.enums.marketplace.SaleComponentStatus;
import com.verygana2.models.enums.marketplace.SaleComponentType;
import com.verygana2.models.enums.marketplace.SalePayerType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.SaleComponent;
import com.verygana2.repositories.marketplace.SaleComponentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Único lugar donde un ítem pasa a Venta Computable (Contrato B 5.9): registra los
 * componentes que financian su precio y lo reconoce solo si suman exactamente el
 * subtotal. Si no cuadran, falla y la transacción entera se deshace: nunca se rellena un
 * faltante para completar el precio.
 *
 * Idempotente por ítem: un ítem ya reconocido no vuelve a registrar componentes, así que
 * un reintento o una entrega posterior no producen otra venta.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaleComponentRecorder {

    private final SaleComponentRepository saleComponentRepository;

    /**
     * Compra de consumidor: la Porción en Llaves de cada ítem la respalda KEYS_RESERVE y el
     * resto lo pagó el usuario en el copago.
     */
    public void recognizeConsumerSale(Purchase purchase, UUID copaymentId) {
        String sourceRef = copaymentId.toString();
        for (PurchaseItem item : purchase.getItems()) {
            long keys = item.getKeysValueCents() == null ? 0L : item.getKeysValueCents();
            long cash = item.getSubtotalCents() - keys;

            List<SaleComponent> components = new ArrayList<>(2);
            if (cash > 0) {
                components.add(SaleComponent.recognized(item, SaleComponentType.USER_CASH,
                        SalePayerType.CONSUMER, sourceRef, cash));
            }
            if (keys > 0) {
                components.add(SaleComponent.recognized(item, SaleComponentType.KEYS_BACKING,
                        SalePayerType.CONSUMER, sourceRef, keys));
            }
            recognize(item, components);
        }
    }

    /**
     * Compra de VERYGANA: el fondo paga el precio completo de cada ítem. El beneficiario no
     * aporta nada, así que no tiene componente.
     */
    public void recognizeInstitutionalSale(Purchase purchase, UUID fundingRef) {
        String sourceRef = fundingRef.toString();
        for (PurchaseItem item : purchase.getItems()) {
            recognize(item, List.of(SaleComponent.recognized(item, SaleComponentType.VERYGANA_PAYMENT,
                    SalePayerType.VERYGANA, sourceRef, item.getSubtotalCents())));
        }
    }

    /**
     * Anula la Venta Computable de un ítem: cada componente reconocido recibe su asiento
     * compensatorio y el original se conserva. Idempotente.
     */
    public void reverse(PurchaseItem item, String cause) {
        if (item.getComputableStatus() != ComputableSaleStatus.RECOGNIZED) {
            return;
        }
        for (SaleComponent component : saleComponentRepository.findByPurchaseItemIdAndStatus(item.getId(),
                SaleComponentStatus.RECOGNIZED)) {
            saleComponentRepository.save(component.reversal(cause));
        }
        item.setComputableStatus(ComputableSaleStatus.REVERSED);
        log.info("[SALE] Venta Computable reversada: itemId={}, causa={}", item.getId(), cause);
    }

    private void recognize(PurchaseItem item, List<SaleComponent> components) {
        if (item.getComputableStatus() == ComputableSaleStatus.RECOGNIZED) {
            return;
        }
        if (item.getComputableStatus() == ComputableSaleStatus.REVERSED) {
            throw new BusinessException("La venta del ítem " + item.getId() + " ya fue reversada");
        }

        long sum = components.stream().mapToLong(SaleComponent::getAmountCents).sum();
        if (sum != item.getSubtotalCents()) {
            throw new BusinessException(String.format(
                    "Los componentes del ítem %d suman %d y el precio reconocido es %d: la venta queda pendiente",
                    item.getId(), sum, item.getSubtotalCents()));
        }

        saleComponentRepository.saveAll(components);
        item.setComputableStatus(ComputableSaleStatus.RECOGNIZED);
        item.setRecognizedAt(ZonedDateTime.now(ZoneOffset.UTC));
    }
}
