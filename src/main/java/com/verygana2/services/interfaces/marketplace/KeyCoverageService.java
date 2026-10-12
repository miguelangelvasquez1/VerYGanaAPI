package com.verygana2.services.interfaces.marketplace;

import java.util.List;

import com.verygana2.dtos.purchase.requests.KeyCoverageQuoteRequestDTO;
import com.verygana2.dtos.purchase.responses.KeyCoverageQuoteResponseDTO;
import com.verygana2.models.marketplace.Product;
import com.verygana2.services.marketplace.KeyCoveragePlan;

/**
 * Porción en Llaves de una compra (Contrato B 2.26, 6.22.8, 7.9): hasta el 50 % ordinario
 * del valor de cada producto, o hasta el 70 % si está en una promoción especial vigente.
 */
public interface KeyCoverageService {

    /**
     * Tope de cobertura de cada unidad de la compra para este consumidor.
     *
     * @param units un producto por unidad comprada, en el orden de los ítems de la compra.
     */
    KeyCoveragePlan planFor(Long consumerId, List<Product> units);

    /**
     * Respaldo promocional disponible para esta compra: lo que KEYS_RESERVE puede pagar
     * sin cruzar su umbral crítico, más el margen de las promociones, que tiene garantía
     * propia.
     */
    long backingAvailableCents(KeyCoveragePlan plan);

    /**
     * Límites del carrito para pagar con llaves, antes de comprar. No depende de cuántas
     * llaves quiera usar el usuario y no reserva llaves, stock ni presupuesto.
     */
    KeyCoverageQuoteResponseDTO quote(Long consumerId, KeyCoverageQuoteRequestDTO request);
}
