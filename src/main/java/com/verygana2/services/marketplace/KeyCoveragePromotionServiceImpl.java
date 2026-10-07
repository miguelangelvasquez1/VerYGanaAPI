package com.verygana2.services.marketplace;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.promotions.ActiveKeyCoveragePromotionResponseDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionOfferResponseDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionRequestDTO;
import com.verygana2.dtos.promotions.KeyCoveragePromotionResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus;
import com.verygana2.models.enums.marketplace.ProductStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotion;
import com.verygana2.models.marketplace.promotions.KeyCoveragePromotionOffer;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionOfferRepository;
import com.verygana2.repositories.marketplace.KeyCoveragePromotionRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.services.interfaces.marketplace.KeyCoveragePromotionService;
import com.verygana2.services.interfaces.marketplace.ProductService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyCoveragePromotionServiceImpl implements KeyCoveragePromotionService {

    @Value("${financial.key-value-cents}")
    private long keyValueCents;

    private final KeyCoveragePromotionRepository promotionRepository;
    private final KeyCoveragePromotionOfferRepository offerRepository;
    private final ProductService productService;
    private final TreasuryService treasuryService;
    private final NotificationService notificationService;
    private final CommissionPolicy commissionPolicy;
    private final Clock clock;

    // ─── Administración ───────────────────────────────────────────────────────

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO create(KeyCoveragePromotionRequestDTO request) {
        KeyCoveragePromotion promotion = promotionRepository.save(KeyCoveragePromotion.draft(toTerms(request)));
        return toResponse(promotion);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO updateDraft(Long promotionId, KeyCoveragePromotionRequestDTO request) {
        KeyCoveragePromotion promotion = lock(promotionId);
        promotion.updateDraft(toTerms(request));
        return toResponse(promotion);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO addOffer(Long promotionId, Long productId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        requireDraft(promotion, "agregar ofertas a");

        Product product = productService.getById(productId);
        if (product.getStatus() != ProductStatus.ACTIVE) {
            throw new BusinessException("Solo un producto activo puede entrar a una promoción");
        }
        CommercialDetails commercial = product.getCommercial();
        if (commercial.getCurrentPlan() == null || commercial.getCurrentPlan().getCode() != PlanCode.STANDARD) {
            throw new BusinessException(
                    "Las promociones especiales de llaves solo aplican a ofertas de Empresas Tipo B (plan STANDARD)");
        }
        if (offerRepository.existsByPromotionIdAndProductId(promotionId, productId)) {
            throw new BusinessException("El producto ya está en esta promoción");
        }
        // Una promoción no se extiende a otra por acumulación: un producto, una promoción.
        if (offerRepository.existsOpenInAnotherPromotion(productId, promotionId)) {
            throw new BusinessException("El producto ya está en otra promoción especial que no ha cerrado");
        }

        offerRepository.save(KeyCoveragePromotionOffer.invite(promotion, product));
        return toResponse(promotion);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO removeOffer(Long promotionId, Long offerId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        requireDraft(promotion, "quitar ofertas de");

        KeyCoveragePromotionOffer offer = offerRepository.findById(offerId)
                .filter(o -> o.getPromotion().getId().equals(promotionId))
                .orElseThrow(() -> new ObjectNotFoundException(
                        "Offer " + offerId + " not found in promotion " + promotionId, KeyCoveragePromotionOffer.class));
        offerRepository.delete(offer);
        offerRepository.flush();
        return toResponse(promotion);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO authorize(Long promotionId, Long adminUserId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        List<KeyCoveragePromotionOffer> offers = offerRepository.findByPromotionIdWithProduct(promotionId);
        if (offers.isEmpty()) {
            throw new BusinessException("La promoción no tiene ofertas: solo aplica a las expresamente incorporadas");
        }

        promotion.authorize(adminUserId, ZonedDateTime.now(clock));

        // "Previamente financiada": el presupuesto queda apartado antes de que la
        // promoción pueda aplicar a una sola compra. Si FORTIFICATION no alcanza, esto
        // lanza y la autorización entera se revierte.
        treasuryService.reservePromotionGuarantee(promotion.getBudgetCents(), treasuryReference(promotion));

        notifyInvitedCommercials(promotion);
        log.info("[KEY-PROMOTION] Promoción {} autorizada por admin {}: {} % hasta {}, presupuesto {}",
                promotionId, adminUserId, promotion.getCoveragePct(), promotion.getEndsAt(),
                promotion.getBudgetCents());
        return toResponse(promotion, offers);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO pause(Long promotionId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        promotion.pause();
        return toResponse(promotion);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO resume(Long promotionId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        promotion.resume();
        return toResponse(promotion);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionResponseDTO end(Long promotionId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        promotion.end();
        returnGuaranteeIfSettled(promotion);
        return toResponse(promotion);
    }

    @Override
    @Transactional(readOnly = true)
    public KeyCoveragePromotionResponseDTO get(Long promotionId) {
        return toResponse(find(promotionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<KeyCoveragePromotionResponseDTO> list(Pageable pageable) {
        return promotionRepository.findAll(pageable).map(this::toResponse);
    }

    // ─── Empresario ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<KeyCoveragePromotionOfferResponseDTO> listOffers(Long commercialId) {
        return offerRepository.findByCommercialIdVisible(commercialId).stream().map(this::toOfferResponse).toList();
    }

    @Override
    @Transactional
    public KeyCoveragePromotionOfferResponseDTO acceptOffer(Long offerId, Long commercialId) {
        KeyCoveragePromotionOffer offer = findOwnedOffer(offerId, commercialId);
        KeyCoveragePromotion promotion = offer.getPromotion();
        ZonedDateTime now = ZonedDateTime.now(clock);

        boolean open = promotion.getStatus() == KeyCoveragePromotionStatus.AUTHORIZED
                || promotion.getStatus() == KeyCoveragePromotionStatus.PAUSED;
        if (!open || !promotion.getEndsAt().isAfter(now)) {
            throw new InvalidStatusException("La promoción ya no admite aceptaciones");
        }

        // Se guarda lo que el empresario está viendo en este momento: es lo que acepta.
        Product product = offer.getProduct();
        long price = product.getPriceCents();
        long maxCoverage = maxCoverageCents(price, promotion.getCoveragePct());
        offer.accept(new KeyCoveragePromotionOffer.AcceptedTerms(
                price, promotion.getCoveragePct(), maxCoverage, price - maxCoverage,
                commissionPolicy.commissionPctFor(product.getCommercial()), promotion.getConfigVersion()), now);

        log.info("[KEY-PROMOTION] Oferta {} aceptada por su empresario (promoción {}, producto {})",
                offerId, promotion.getId(), product.getId());
        return toOfferResponse(offer);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionOfferResponseDTO rejectOffer(Long offerId, Long commercialId) {
        KeyCoveragePromotionOffer offer = findOwnedOffer(offerId, commercialId);
        offer.reject(ZonedDateTime.now(clock));
        return toOfferResponse(offer);
    }

    @Override
    @Transactional
    public KeyCoveragePromotionOfferResponseDTO withdrawOffer(Long offerId, Long commercialId) {
        KeyCoveragePromotionOffer offer = findOwnedOffer(offerId, commercialId);
        offer.withdraw(ZonedDateTime.now(clock));
        return toOfferResponse(offer);
    }

    // ─── Consumidor ───────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<ActiveKeyCoveragePromotionResponseDTO> listActive() {
        return offerRepository.findAllLive(ZonedDateTime.now(clock)).stream()
                .filter(offer -> offer.getProduct().getStatus() == ProductStatus.ACTIVE)
                .map(offer -> new ActiveKeyCoveragePromotionResponseDTO(
                        offer.getProduct().getId(),
                        offer.getPromotion().getName(),
                        offer.getPromotion().getCoveragePct(),
                        offer.getPromotion().getEndsAt(),
                        offer.getPromotion().getTermsText()))
                .toList();
    }

    // ─── Ciclo de una compra ──────────────────────────────────────────────────

    @Override
    @Transactional
    public void reserveForPurchase(Purchase purchase) {
        Map<Long, Long> excessByPromotion = excessByPromotion(purchase);
        if (excessByPromotion.isEmpty()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        for (KeyCoveragePromotion promotion : promotionRepository.findAllByIdForUpdate(excessByPromotion.keySet())) {
            // El plan se armó sin bloqueo: entre tanto pudo pausarse, cerrarse o agotarse.
            if (!promotion.isLiveAt(now)) {
                throw new BusinessException("La promoción \"" + promotion.getName() + "\" ya no está vigente");
            }
            promotion.reserve(excessByPromotion.get(promotion.getId()));
        }
    }

    @Override
    @Transactional
    public void confirmForPurchase(Purchase purchase, long keysOutflowCents) {
        Map<Long, Long> excessByPromotion = excessByPromotion(purchase);
        if (excessByPromotion.isEmpty()) {
            return;
        }
        for (KeyCoveragePromotion promotion : promotionRepository.findAllByIdForUpdate(excessByPromotion.keySet())) {
            long excess = excessByPromotion.get(promotion.getId());
            promotion.confirm(excess);
            long drawn = treasuryService.drawPromotionGuarantee(excess, keysOutflowCents, treasuryReference(promotion));
            if (drawn > 0) {
                promotion.registerGuaranteeDrawn(drawn);
            }
        }
    }

    @Override
    @Transactional
    public void releaseForPurchase(Purchase purchase) {
        Map<Long, Long> excessByPromotion = excessByPromotion(purchase);
        if (excessByPromotion.isEmpty()) {
            return;
        }
        for (KeyCoveragePromotion promotion : promotionRepository.findAllByIdForUpdate(excessByPromotion.keySet())) {
            promotion.release(excessByPromotion.get(promotion.getId()));
        }
    }

    // ─── Cierre ───────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<Long> idsNeedingClosure() {
        List<Long> ids = new ArrayList<>(promotionRepository.findIdsPastEndNotEnded(ZonedDateTime.now(clock)));
        ids.addAll(promotionRepository.findIdsEndedWithGuaranteeToReturn());
        return ids.stream().distinct().toList();
    }

    @Override
    @Transactional
    public void close(Long promotionId) {
        KeyCoveragePromotion promotion = lock(promotionId);
        boolean running = promotion.getStatus() == KeyCoveragePromotionStatus.AUTHORIZED
                || promotion.getStatus() == KeyCoveragePromotionStatus.PAUSED;
        if (running && !promotion.getEndsAt().isAfter(ZonedDateTime.now(clock))) {
            promotion.end();
            log.info("[KEY-PROMOTION] Promoción {} cerrada por fin de vigencia", promotionId);
        }
        returnGuaranteeIfSettled(promotion);
    }

    /**
     * Devuelve a FORTIFICATION la garantía no usada. Espera a que no queden compras con
     * pago en curso: una de ellas todavía podría necesitarla al aprobarse.
     */
    private void returnGuaranteeIfSettled(KeyCoveragePromotion promotion) {
        if (promotion.getStatus() != KeyCoveragePromotionStatus.ENDED
                || !promotion.hasGuarantee()
                || promotion.getReservedCents() > 0) {
            return;
        }
        long returnable = promotion.returnableGuaranteeCents();
        if (returnable > 0) {
            treasuryService.returnPromotionGuarantee(returnable, treasuryReference(promotion));
        }
        promotion.markGuaranteeReturned(ZonedDateTime.now(clock));
        log.info("[KEY-PROMOTION] Garantía de la promoción {} devuelta a FORTIFICATION: {} centavos",
                promotion.getId(), returnable);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /** En orden de id (TreeMap), el mismo en que se bloquean las promociones. */
    private static Map<Long, Long> excessByPromotion(Purchase purchase) {
        Map<Long, Long> excess = new TreeMap<>();
        for (PurchaseItem item : purchase.getItems()) {
            Long promotionId = item.getKeyCoveragePromotionId();
            Long itemExcess = item.getPromotionExcessCents();
            if (promotionId != null && itemExcess != null && itemExcess > 0) {
                excess.merge(promotionId, itemExcess, Long::sum);
            }
        }
        return excess;
    }

    private void notifyInvitedCommercials(KeyCoveragePromotion promotion) {
        for (Long commercialId : offerRepository.findCommercialIdsByPromotionId(promotion.getId())) {
            notificationService.createInternalNotification(commercialId,
                    "Invitación a promoción especial de llaves",
                    "Tienes ofertas invitadas a la promoción \"" + promotion.getName() + "\", que permite cubrir hasta el "
                            + promotion.getCoveragePct() + " % con Llaves Promocionales. Revisa las condiciones y "
                            + "decide si aceptas: no participas hasta que lo hagas.",
                    Instant.now(clock));
        }
    }

    private KeyCoveragePromotion lock(Long promotionId) {
        return promotionRepository.findByIdForUpdate(promotionId).orElseThrow(() -> notFound(promotionId));
    }

    private KeyCoveragePromotion find(Long promotionId) {
        return promotionRepository.findById(promotionId).orElseThrow(() -> notFound(promotionId));
    }

    private static ObjectNotFoundException notFound(Long promotionId) {
        return new ObjectNotFoundException("Promotion with id: " + promotionId + " not found",
                KeyCoveragePromotion.class);
    }

    private KeyCoveragePromotionOffer findOwnedOffer(Long offerId, Long commercialId) {
        return offerRepository.findByIdAndCommercialId(offerId, commercialId)
                .orElseThrow(() -> new ObjectNotFoundException("Offer with id: " + offerId + " not found",
                        KeyCoveragePromotionOffer.class));
    }

    private static void requireDraft(KeyCoveragePromotion promotion, String action) {
        if (promotion.getStatus() != KeyCoveragePromotionStatus.DRAFT) {
            throw new InvalidStatusException(
                    "No se puede " + action + " una promoción en estado " + promotion.getStatus());
        }
    }

    private static UUID treasuryReference(KeyCoveragePromotion promotion) {
        return UUID.nameUUIDFromBytes(("key-coverage-promotion-" + promotion.getId()).getBytes());
    }

    private static KeyCoveragePromotion.Terms toTerms(KeyCoveragePromotionRequestDTO request) {
        return new KeyCoveragePromotion.Terms(request.name(), request.coveragePct(), request.startsAt(),
                request.endsAt(), request.budgetCents(), request.maxExcessCentsPerUser(), request.termsText());
    }

    /** Cobertura máxima en llaves enteras: no se cubre con fracciones de llave. */
    private long maxCoverageCents(long priceCents, int pct) {
        return priceCents * pct / 100 / keyValueCents * keyValueCents;
    }

    private KeyCoveragePromotionResponseDTO toResponse(KeyCoveragePromotion promotion) {
        return toResponse(promotion, offerRepository.findByPromotionIdWithProduct(promotion.getId()));
    }

    private KeyCoveragePromotionResponseDTO toResponse(KeyCoveragePromotion promotion,
            List<KeyCoveragePromotionOffer> offers) {
        return new KeyCoveragePromotionResponseDTO(
                promotion.getId(),
                promotion.getName(),
                promotion.getCoveragePct(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getStatus(),
                promotion.getConfigVersion(),
                promotion.getTermsText(),
                promotion.getBudgetCents(),
                promotion.getReservedCents(),
                promotion.getConsumedCents(),
                promotion.availableBudgetCents(),
                promotion.getMaxExcessCentsPerUser(),
                promotion.getGuaranteeDrawnCents(),
                promotion.getGuaranteeReturnedAt(),
                promotion.getAuthorizedAt(),
                offers.stream().map(this::toOfferResponse).toList());
    }

    private KeyCoveragePromotionOfferResponseDTO toOfferResponse(KeyCoveragePromotionOffer offer) {
        KeyCoveragePromotion promotion = offer.getPromotion();
        Product product = offer.getProduct();
        boolean accepted = offer.getStatus() == KeyCoveragePromotionOfferStatus.ACCEPTED
                || offer.getStatus() == KeyCoveragePromotionOfferStatus.WITHDRAWN;

        // Aceptada: lo que aceptó. Pendiente: lo vigente, que es lo que aceptaría hoy.
        long price = accepted ? offer.getAcceptedPriceCents() : product.getPriceCents();
        int promotionPct = accepted ? offer.getAcceptedCoveragePct() : promotion.getCoveragePct();
        long maxCoverage = accepted ? offer.getAcceptedMaxCoverageCents() : maxCoverageCents(price, promotionPct);
        int commissionPct = accepted
                ? offer.getAcceptedCommissionPct()
                : commissionPolicy.commissionPctFor(product.getCommercial());
        long commission = price * commissionPct / 100;

        return new KeyCoveragePromotionOfferResponseDTO(
                offer.getId(),
                offer.getStatus(),
                offer.getRespondedAt(),
                promotion.getId(),
                promotion.getName(),
                promotion.getStatus(),
                promotion.getStartsAt(),
                promotion.getEndsAt(),
                promotion.getTermsText(),
                product.getId(),
                product.getName(),
                price,
                Math.min(product.getMaxKeysPct(), KeyCoveragePromotion.ORDINARY_MAX_PCT),
                promotionPct,
                maxCoverage,
                price - maxCoverage,
                commissionPct,
                commission,
                price - commission);
    }
}
