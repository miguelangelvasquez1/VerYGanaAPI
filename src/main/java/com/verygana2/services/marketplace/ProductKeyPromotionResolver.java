package com.verygana2.services.marketplace;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.product.responses.ProductKeyPromotionDTO;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotionOffer;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionOfferRepository;

import lombok.RequiredArgsConstructor;

/**
 * Marca en los DTOs de producto cuáles tienen hoy una promoción especial de cobertura con
 * llaves, para que el catálogo la anuncie. Una promoción vigente pero sin presupuesto para
 * una unidad del producto ya no se anuncia.
 *
 * Trabaja sobre la página ya mapeada y hace una sola consulta para todos sus productos,
 * no una por producto. Vive aparte de KeyCoveragePromotionServiceImpl porque ese depende
 * de ProductService, y aquí es ProductService quien lo necesita.
 */
@Component
@RequiredArgsConstructor
public class ProductKeyPromotionResolver {

    @Value("${financial.key-value-cents}")
    private long keyValueCents;

    private final KeyCoveragePromotionOfferRepository offerRepository;
    private final Clock clock;

    /**
     * Asigna a cada DTO su promoción vigente; los que no tienen quedan como estaban (null).
     *
     * @param price precio del producto en pesos, como lo exponen los DTOs.
     */
    @Transactional(readOnly = true)
    public <T> void decorate(Collection<T> dtos, Function<T, Long> productId, Function<T, BigDecimal> price,
            BiConsumer<T, ProductKeyPromotionDTO> setter) {
        List<Long> ids = dtos.stream().map(productId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }

        Map<Long, KeyCoveragePromotion> liveByProduct = new HashMap<>();
        for (KeyCoveragePromotionOffer offer : offerRepository.findLiveByProductIds(ids, ZonedDateTime.now(clock))) {
            Product product = offer.getProduct();
            // Presupuesto agotado: la promoción deja de anunciarse y el producto vuelve
            // a mostrar su tope ordinario, que es el que la compra le va a dar.
            if (offer.getPromotion().canCoverOneUnit(product.getPriceCents(), product.getMaxKeysPct(), keyValueCents)) {
                liveByProduct.put(product.getId(), offer.getPromotion());
            }
        }
        if (liveByProduct.isEmpty()) {
            return;
        }

        for (T dto : dtos) {
            KeyCoveragePromotion promotion = liveByProduct.get(productId.apply(dto));
            BigDecimal productPrice = price.apply(dto);
            if (promotion != null && productPrice != null) {
                setter.accept(dto, toDto(promotion, productPrice));
            }
        }
    }

    private ProductKeyPromotionDTO toDto(KeyCoveragePromotion promotion, BigDecimal pricePesos) {
        long priceCents = pricePesos.movePointRight(2).longValue();
        // Llaves enteras, igual que KeyCoverageServiceImpl: no se cubre con fracciones de llave.
        long maxKeys = priceCents * promotion.getCoveragePct() / 100 / keyValueCents;
        return new ProductKeyPromotionDTO(
                promotion.getCoveragePct(),
                maxKeys,
                priceCents - maxKeys * keyValueCents,
                promotion.getEndsAt());
    }
}
