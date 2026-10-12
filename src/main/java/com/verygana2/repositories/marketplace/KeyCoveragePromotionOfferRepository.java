package com.verygana2.repositories.marketplace;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.marketplace.promotions.KeyCoveragePromotionOffer;

@Repository
public interface KeyCoveragePromotionOfferRepository extends JpaRepository<KeyCoveragePromotionOffer, Long> {

    /**
     * Ofertas que hoy suben el tope de alguno de estos productos: aceptadas por su
     * empresario, de una promoción autorizada y dentro de su vigencia.
     */
    @Query("""
            SELECT o FROM KeyCoveragePromotionOffer o
            JOIN FETCH o.promotion p
            JOIN FETCH o.product
            WHERE o.product.id IN :productIds
              AND o.status = com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus.ACCEPTED
              AND p.status = com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.AUTHORIZED
              AND p.startsAt <= :now
              AND p.endsAt > :now
            """)
    List<KeyCoveragePromotionOffer> findLiveByProductIds(@Param("productIds") Collection<Long> productIds,
            @Param("now") ZonedDateTime now);

    /** Todo lo que hoy está en promoción, para que el catálogo lo pueda anunciar. */
    @Query("""
            SELECT o FROM KeyCoveragePromotionOffer o
            JOIN FETCH o.promotion p
            JOIN FETCH o.product
            WHERE o.status = com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus.ACCEPTED
              AND p.status = com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.AUTHORIZED
              AND p.startsAt <= :now
              AND p.endsAt > :now
            ORDER BY p.endsAt ASC, o.id ASC
            """)
    List<KeyCoveragePromotionOffer> findAllLive(@Param("now") ZonedDateTime now);

    /** ¿El producto ya está invitado o aceptado en otra promoción que no ha cerrado? */
    @Query("""
            SELECT COUNT(o) > 0 FROM KeyCoveragePromotionOffer o
            WHERE o.product.id = :productId
              AND o.promotion.id <> :promotionId
              AND o.status IN (com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus.PENDING_ACCEPTANCE,
                               com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus.ACCEPTED)
              AND o.promotion.status <> com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.ENDED
            """)
    boolean existsOpenInAnotherPromotion(@Param("productId") Long productId, @Param("promotionId") Long promotionId);

    boolean existsByPromotionIdAndProductId(Long promotionId, Long productId);

    @Query("""
            SELECT o FROM KeyCoveragePromotionOffer o
            JOIN FETCH o.product
            WHERE o.promotion.id = :promotionId
            ORDER BY o.id ASC
            """)
    List<KeyCoveragePromotionOffer> findByPromotionIdWithProduct(@Param("promotionId") Long promotionId);

    /** Invitaciones de un empresario. Los borradores no se le muestran: aún no hay nada que aceptar. */
    @Query("""
            SELECT o FROM KeyCoveragePromotionOffer o
            JOIN FETCH o.promotion p
            JOIN FETCH o.product
            WHERE o.commercialId = :commercialId
              AND p.status <> com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus.DRAFT
            ORDER BY o.id DESC
            """)
    List<KeyCoveragePromotionOffer> findByCommercialIdVisible(@Param("commercialId") Long commercialId);

    @Query("""
            SELECT o FROM KeyCoveragePromotionOffer o
            JOIN FETCH o.promotion
            JOIN FETCH o.product
            WHERE o.id = :offerId AND o.commercialId = :commercialId
            """)
    Optional<KeyCoveragePromotionOffer> findByIdAndCommercialId(@Param("offerId") Long offerId,
            @Param("commercialId") Long commercialId);

    @Query("SELECT DISTINCT o.commercialId FROM KeyCoveragePromotionOffer o WHERE o.promotion.id = :promotionId")
    List<Long> findCommercialIdsByPromotionId(@Param("promotionId") Long promotionId);
}
