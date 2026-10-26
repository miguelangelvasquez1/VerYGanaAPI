package com.verygana2.services.marketplace;

import java.security.SecureRandom;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.verygana2.models.enums.marketplace.ProductType;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.ProductStockRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Entrega el código de cada ítem de una compra ya pagada. Los productos DIGITAL quedan
 * CLAIMED de inmediato (no hay paso de reclamación separado: el proceso de redención
 * ocurre fuera de la plataforma). Los PHYSICAL quedan PENDING —esperando que el
 * comerciante valide el PIN al momento de la entrega— y generan un PIN de reclamación
 * que va, junto con el código, en el correo de confirmación.
 *
 * Lo usan la compra de un consumidor y la de VERYGANA: en la segunda el correo va al
 * beneficiario, pero la entrega es la misma.
 */
@Slf4j
@Component
public class PurchaseDeliverer {

    private static final int CLAIM_PIN_MAX_VALUE = 1_000_000; // 6 dígitos, mismo formato que EmailVerificationServiceImpl

    private final ProductStockRepository productStockRepository;
    private final PasswordEncoder passwordEncoder;
    private final int claimExpirationDays;
    private final SecureRandom secureRandom = new SecureRandom();

    public PurchaseDeliverer(ProductStockRepository productStockRepository, PasswordEncoder passwordEncoder,
            @Value("${marketplace.claim.expiration-days:15}") int claimExpirationDays) {
        this.productStockRepository = productStockRepository;
        this.passwordEncoder = passwordEncoder;
        this.claimExpirationDays = claimExpirationDays;
    }

    public void deliver(Purchase purchase) {
        List<PurchaseItem> items = purchase.getItems();
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

        for (PurchaseItem item : items) {
            ProductStock stock = item.getAssignedProductStock();
            stock.markAsSold(item);
            productStockRepository.save(stock);
            item.setDeliveredCode(stock.getCode());
            item.setDeliveredAt(now);

            Product product = item.getProduct();
            if (product != null && product.getProductType() == ProductType.PHYSICAL) {
                String pin = generateClaimPin();
                item.setClaimPinHash(passwordEncoder.encode(pin));
                item.setClaimExpiresAt(now.plusDays(claimExpirationDays));
                item.setStatus(PurchaseItemStatus.PENDING);
                item.setPlainClaimPinForEmail(pin);
            } else {
                item.setClaimedAt(now);
                item.setStatus(PurchaseItemStatus.CLAIMED);
            }
        }
        log.debug("[DELIVERY] {} código(s) entregado(s) para purchaseId={}", items.size(), purchase.getId());
    }

    private String generateClaimPin() {
        return String.format("%06d", secureRandom.nextInt(CLAIM_PIN_MAX_VALUE));
    }
}
