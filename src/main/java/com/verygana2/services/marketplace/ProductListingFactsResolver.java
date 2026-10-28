package com.verygana2.services.marketplace;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.repositories.marketplace.ProductListingFacts;
import com.verygana2.repositories.marketplace.ProductRepository;

import lombok.RequiredArgsConstructor;

/**
 * Resuelve, con una sola consulta por página, el stock disponible y los datos del comercio
 * de los productos de un listado.
 *
 * Existe para evitar un N+1: leer {@code Product.getAvailableStock()} carga todas las
 * unidades del inventario (con su código cifrado) y una por una sus compras, y leer el
 * comercio inicializa {@code CommercialDetails}, su usuario y sus relaciones inversas, por
 * cada comercio de la página. Aquí el stock se cuenta en la base con el mismo criterio de
 * {@code ProductStock.isExpired()} y no se lee ningún código.
 *
 * Mismo patrón que {@link ProductKeyPromotionResolver}. No sirve a la compra: allí manda
 * {@code Product.getAvailableStock()} sobre las unidades reales.
 */
@Component
@RequiredArgsConstructor
public class ProductListingFactsResolver {

    private final ProductRepository productRepository;
    private final Clock clock;

    /**
     * Devuelve los hechos de cada id pedido. Un id que la consulta no devuelve (no debería
     * ocurrir: los ids salen de la página) queda con stock 0 y sin datos de comercio.
     */
    @Transactional(readOnly = true)
    public Map<Long, ProductListingFacts> resolve(Collection<Long> productIds) {
        Set<Long> ids = new LinkedHashSet<>(productIds);
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, ProductListingFacts> facts = new HashMap<>();
        for (ProductListingFacts row : productRepository.findListingFacts(ids, ZonedDateTime.now(clock))) {
            facts.put(row.productId(), row);
        }
        for (Long id : ids) {
            facts.computeIfAbsent(id, missing -> new ProductListingFacts(missing, null, null, 0L));
        }
        return facts;
    }
}
