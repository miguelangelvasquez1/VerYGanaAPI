package com.verygana2.services.interfaces.marketplace;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.promotions.ActiveKeyCoveragePromotionResponseDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionOfferResponseDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionRequestDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionResponseDTO;
import com.verygana2.models.marketplace.Purchase;

/**
 * Promociones especiales de cobertura con llaves (Contrato B 6.22.8, 7.9.2): el admin
 * las crea y autoriza, cada empresario acepta incorporar su oferta, y mientras estén
 * vigentes suben el tope del 50 % ordinario hasta un máximo del 70 %.
 */
public interface KeyCoveragePromotionService {

    // ─── Administración ───────────────────────────────────────────────────────

    KeyCoveragePromotionResponseDTO create(KeyCoveragePromotionRequestDTO request);

    KeyCoveragePromotionResponseDTO updateDraft(Long promotionId, KeyCoveragePromotionRequestDTO request);

    KeyCoveragePromotionResponseDTO addOffer(Long promotionId, Long productId);

    KeyCoveragePromotionResponseDTO removeOffer(Long promotionId, Long offerId);

    /** Autoriza y aparta el presupuesto como garantía desde FORTIFICATION. Sin fondos no se autoriza. */
    KeyCoveragePromotionResponseDTO authorize(Long promotionId, Long adminUserId);

    KeyCoveragePromotionResponseDTO pause(Long promotionId);

    KeyCoveragePromotionResponseDTO resume(Long promotionId);

    KeyCoveragePromotionResponseDTO end(Long promotionId);

    KeyCoveragePromotionResponseDTO get(Long promotionId);

    Page<KeyCoveragePromotionResponseDTO> list(Pageable pageable);

    // ─── Empresario ───────────────────────────────────────────────────────────

    List<KeyCoveragePromotionOfferResponseDTO> listOffers(Long commercialId);

    KeyCoveragePromotionOfferResponseDTO acceptOffer(Long offerId, Long commercialId);

    KeyCoveragePromotionOfferResponseDTO rejectOffer(Long offerId, Long commercialId);

    KeyCoveragePromotionOfferResponseDTO withdrawOffer(Long offerId, Long commercialId);

    // ─── Consumidor ───────────────────────────────────────────────────────────

    List<ActiveKeyCoveragePromotionResponseDTO> listActive();

    // ─── Ciclo de una compra ──────────────────────────────────────────────────

    /** Compromete el presupuesto que los ítems de la compra toman de sus promociones. */
    void reserveForPurchase(Purchase purchase);

    /**
     * Pago aprobado: lo comprometido pasa a consumido y la garantía repone KEYS_RESERVE
     * si pagar {@code keysOutflowCents} lo dejaría bajo el umbral crítico.
     */
    void confirmForPurchase(Purchase purchase, long keysOutflowCents);

    /** Pago fallido o vencido: lo comprometido vuelve a estar disponible. */
    void releaseForPurchase(Purchase purchase);

    // ─── Cierre ───────────────────────────────────────────────────────────────

    /** Promociones con vigencia terminada sin cerrar, o cerradas con garantía por devolver. */
    List<Long> idsNeedingClosure();

    /** Cierra la promoción si ya venció y devuelve a FORTIFICATION la garantía no usada. Idempotente. */
    void close(Long promotionId);
}
