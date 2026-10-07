package com.verygana2.services.marketplace;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.purchase.requests.CreatePurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.KeyCoverageQuoteRequestDTO;
import com.verygana2.dtos.purchase.responses.KeyCoverageQuoteResponseDTO;
import com.verygana2.exceptions.ProductNotAvailableException;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotionOffer;
import com.verygana2.repositories.finance.KeyWalletRepository;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionOfferRepository;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.finance.KeyLedgerService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.KeyCoverageService;
import com.verygana2.services.interfaces.marketplace.ProductService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class KeyCoverageServiceImpl implements KeyCoverageService {

    @Value("${financial.key-value-cents}")
    private long keyValueCents;

    private final KeyCoveragePromotionOfferRepository offerRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final KeyWalletRepository keyWalletRepository;
    private final KeyLedgerService keyLedgerService;
    private final TreasuryService treasuryService;
    private final ProductService productService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public KeyCoveragePlan planFor(Long consumerId, List<Product> units) {
        ZonedDateTime now = ZonedDateTime.now(clock);

        List<Long> productIds = units.stream().map(Product::getId).distinct().toList();
        Map<Long, KeyCoveragePromotion> liveByProduct = new HashMap<>();
        for (KeyCoveragePromotionOffer offer : offerRepository.findLiveByProductIds(productIds, now)) {
            liveByProduct.put(offer.getProduct().getId(), offer.getPromotion());
        }

        // Presupuesto y límite por usuario se van descontando unidad a unidad, con el
        // margen completo de cada una: lo que el plan promete tiene que caber aunque el
        // usuario aplique el máximo.
        Map<Long, Long> budgetLeft = new HashMap<>();
        Map<Long, Long> userLeft = new HashMap<>();

        List<KeyCoveragePlan.Unit> planned = new ArrayList<>(units.size());
        for (Product product : units) {
            long price = product.getPriceCents();
            int ordinaryPct = Math.min(product.getMaxKeysPct(), KeyCoveragePromotion.ORDINARY_MAX_PCT);
            long ordinaryLimit = wholeKeysCents(price * ordinaryPct / 100);

            KeyCoveragePromotion promotion = liveByProduct.get(product.getId());
            if (promotion != null) {
                long promotionLimit = wholeKeysCents(price * promotion.getCoveragePct() / 100);
                long excess = promotionLimit - ordinaryLimit;
                long budget = budgetLeft.computeIfAbsent(promotion.getId(), id -> promotion.availableBudgetCents());
                long user = userLeft.computeIfAbsent(promotion.getId(), id -> userHeadroom(promotion, consumerId));

                if (excess > 0 && excess <= budget && excess <= user) {
                    budgetLeft.put(promotion.getId(), budget - excess);
                    userLeft.put(promotion.getId(), user - excess);
                    planned.add(new KeyCoveragePlan.Unit(product.getId(), price, ordinaryPct, ordinaryLimit,
                            promotion.getId(), promotion.getCoveragePct(), promotionLimit, promotion.getEndsAt()));
                    continue;
                }
            }
            // Sin promoción, o con una que ya no alcanza: rige el tope ordinario. No es un error.
            planned.add(new KeyCoveragePlan.Unit(product.getId(), price, ordinaryPct, ordinaryLimit,
                    null, ordinaryPct, ordinaryLimit, null));
        }
        return new KeyCoveragePlan(planned);
    }

    @Override
    @Transactional(readOnly = true)
    public long backingAvailableCents(KeyCoveragePlan plan) {
        return treasuryService.keysReserveHeadroomCents() + plan.maxPromotionExcessCents();
    }

    @Override
    @Transactional(readOnly = true)
    public KeyCoverageQuoteResponseDTO quote(Long consumerId, KeyCoverageQuoteRequestDTO request) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        Map<Long, Product> products = new HashMap<>();
        List<Product> units = new ArrayList<>();
        for (CreatePurchaseItemRequestDTO line : request.getItems()) {
            Product product = productService.getById(line.getProductId());
            if (product.getStatus() != ProductStatus.ACTIVE) {
                throw new ProductNotAvailableException(product.getName());
            }
            products.put(product.getId(), product);
            quantities.merge(product.getId(), line.getQuantity(), Integer::sum);
            for (int i = 0; i < line.getQuantity(); i++) {
                units.add(product);
            }
        }

        KeyCoveragePlan plan = planFor(consumerId, units);

        long spendableCents = keyWalletRepository.findByConsumerId(consumerId)
                .map(this::spendableWholeKeysCents)
                .orElse(0L);
        long maxCoverageCents = plan.maxCoverageCents();
        long backingCents = wholeKeysCents(backingAvailableCents(plan));

        // Lo que el usuario podría cubrir por tope y saldo, antes de mirar el respaldo.
        long reachableCents = Math.min(maxCoverageCents, spendableCents);
        long coverableCents = Math.min(reachableCents, backingCents);
        long requestedCents = request.getKeysToUse() == null
                ? coverableCents
                : request.getKeysToUse() * keyValueCents;
        long effectiveCents = Math.min(coverableCents, requestedCents);

        long totalCents = plan.totalCents();
        return new KeyCoverageQuoteResponseDTO(
                totalCents,
                spendableCents / keyValueCents,
                maxCoverageCents / keyValueCents,
                requestedCents / keyValueCents,
                effectiveCents / keyValueCents,
                effectiveCents,
                (maxCoverageCents - reachableCents) / keyValueCents,
                totalCents - effectiveCents,
                totalCents - maxCoverageCents,
                backingCents >= reachableCents,
                quoteItems(plan, products, quantities));
    }

    private List<KeyCoverageQuoteResponseDTO.Item> quoteItems(KeyCoveragePlan plan, Map<Long, Product> products,
            Map<Long, Integer> quantities) {
        // Una línea por producto. Si la promoción no alcanzó para todas sus unidades, se
        // muestra el tope de la primera, que es la que la obtiene.
        Map<Long, KeyCoveragePlan.Unit> firstUnit = new LinkedHashMap<>();
        for (KeyCoveragePlan.Unit unit : plan.units()) {
            firstUnit.putIfAbsent(unit.productId(), unit);
        }
        return firstUnit.values().stream()
                .map(unit -> new KeyCoverageQuoteResponseDTO.Item(
                        unit.productId(),
                        products.get(unit.productId()).getName(),
                        quantities.get(unit.productId()),
                        unit.priceCents(),
                        unit.pctApplied(),
                        unit.limitCents() / keyValueCents,
                        unit.promotionId() != null,
                        unit.promotionEndsAt()))
                .toList();
    }

    private long userHeadroom(KeyCoveragePromotion promotion, Long consumerId) {
        if (promotion.getMaxExcessCentsPerUser() == null) {
            return Long.MAX_VALUE;
        }
        long used = purchaseItemRepository.sumPromotionExcessByConsumer(promotion.getId(), consumerId);
        return Math.max(0L, promotion.getMaxExcessCentsPerUser() - used);
    }

    private long spendableWholeKeysCents(KeyWallet wallet) {
        return wholeKeysCents(keyLedgerService.spendablePurchaseCents(wallet));
    }

    /** No se cubre con fracciones de llave: se baja al múltiplo del valor de una llave. */
    private long wholeKeysCents(long cents) {
        return cents / keyValueCents * keyValueCents;
    }
}
