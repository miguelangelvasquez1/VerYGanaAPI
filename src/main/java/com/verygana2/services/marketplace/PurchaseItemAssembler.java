package com.verygana2.services.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.exceptions.ProductNotAvailableException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductStock;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.marketplace.ProductRepository;
import com.verygana2.repositories.marketplace.ProductStockRepository;
import com.verygana2.services.interfaces.marketplace.ProductService;

import lombok.RequiredArgsConstructor;

/**
 * Arma los ítems de una compra reservando una unidad de stock por cada uno. Es el mismo
 * paso para la compra de un consumidor y para la de VERYGANA (Contrato B 10.16): solo
 * cambia quién compra, no cómo se reserva ni cómo se fija el precio y la comisión.
 */
@Component
@RequiredArgsConstructor
public class PurchaseItemAssembler {

    private final ProductService productService;
    private final ProductRepository productRepository;
    private final ProductStockRepository productStockRepository;
    private final CommissionPolicy commissionPolicy;
    private final TreasuryConfig treasuryConfig;

    /**
     * Agrega {@code quantity} ítems del producto a la compra, cada uno con su unidad de stock
     * reservada y su comisión provisional sobre el precio completo. La definitiva se fija al
     * reconocer la venta, descontando lo que absorba el Saldo de Prosperidad.
     *
     * @return el producto, ya con su conteo de stock actualizado
     */
    @SuppressWarnings("null")
    public Product addItems(Purchase purchase, Long productId, int quantity) {
        Product product = productService.getById(productId);

        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new ProductNotAvailableException(product.getName());
        }
        if (product.getAvailableStock() < quantity) {
            throw new InsufficientStockException(product.getName());
        }

        CommercialDetails commercial = product.getCommercial();
        int commissionPct = commissionPolicy.commissionPctFor(commercial);

        for (int i = 0; i < quantity; i++) {
            ProductStock stock = productStockRepository
                    .findNextAvailableForProduct(product.getId())
                    .orElseThrow(() -> new InsufficientStockException(product.getName()));

            stock.markAsReserved();
            productStockRepository.save(stock);

            long unitPriceCents = product.getPriceCents();

            PurchaseItem item = PurchaseItem.builder()
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .commercialId(commercial.getId())
                    .assignedProductStock(stock)
                    .unitPriceCents(unitPriceCents)
                    .subtotalCents(unitPriceCents)
                    .commissionPctApplied(commissionPct)
                    .commercialActivityTypeAtPurchase(commercial.getCommercialActivityType())
                    .maxKeysPctAtPurchase(product.getMaxKeysPct())
                    .status(PurchaseItemStatus.PENDING)
                    .createdAt(ZonedDateTime.now(ZoneOffset.UTC))
                    .build();
            item.applyCommission(unitPriceCents, treasuryConfig.getVatPct());

            purchase.addItem(item);
        }

        product.updateStockCount();

        // La sección de recompensas de juegos no filtra por stock (ver
        // ProductRepository.findGameRewardsProducts), así que hay que apagar
        // isGameReward manualmente al agotarse para no ofrecer un producto
        // sin unidades disponibles.
        if (Boolean.TRUE.equals(product.getIsGameReward()) && product.getAvailableStock() == 0) {
            product.setIsGameReward(false);
            product.setGameRewardAutoDisabled(true);
        }

        productRepository.save(product);
        return product;
    }
}
