package com.verygana2.models.marketplace.promotions;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionOfferStatus;
import com.verygana2.models.marketplace.Product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Incorporación de un producto a una promoción especial. El empresario no está obligado
 * a participar (6.22.8): la promoción solo aplica a su producto si la aceptó, y lo que
 * aceptó queda guardado tal cual lo vio.
 */
@Entity
@Table(name = "key_coverage_promotion_offers", uniqueConstraints = @UniqueConstraint(
        name = "uk_kcpo_promotion_product", columnNames = { "promotion_id", "product_id" }))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeyCoveragePromotionOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promotion_id", nullable = false, updatable = false)
    private KeyCoveragePromotion promotion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, updatable = false)
    private Product product;

    /** Dueño del producto al invitarlo; evita navegar product.commercial para filtrar. */
    @Column(name = "commercial_id", nullable = false, updatable = false)
    private Long commercialId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private KeyCoveragePromotionOfferStatus status;

    @Column(name = "responded_at")
    private ZonedDateTime respondedAt;

    // ─── Lo que el empresario aceptó (null hasta que acepta) ──────────────────

    @Column(name = "accepted_price_cents")
    private Long acceptedPriceCents;

    @Column(name = "accepted_coverage_pct")
    private Integer acceptedCoveragePct;

    @Column(name = "accepted_max_coverage_cents")
    private Long acceptedMaxCoverageCents;

    @Column(name = "accepted_min_user_cash_cents")
    private Long acceptedMinUserCashCents;

    @Column(name = "accepted_commission_pct")
    private Integer acceptedCommissionPct;

    @Column(name = "accepted_config_version")
    private Integer acceptedConfigVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    /** Condiciones económicas tal como se le muestran al empresario al aceptar. */
    public record AcceptedTerms(long priceCents, int coveragePct, long maxCoverageCents, long minUserCashCents,
            int commissionPct, int configVersion) {
    }

    public static KeyCoveragePromotionOffer invite(KeyCoveragePromotion promotion, Product product) {
        KeyCoveragePromotionOffer offer = new KeyCoveragePromotionOffer();
        offer.promotion = promotion;
        offer.product = product;
        offer.commercialId = product.getCommercial().getId();
        offer.status = KeyCoveragePromotionOfferStatus.PENDING_ACCEPTANCE;
        return offer;
    }

    public void accept(AcceptedTerms terms, ZonedDateTime now) {
        requirePending("aceptar");
        this.status = KeyCoveragePromotionOfferStatus.ACCEPTED;
        this.respondedAt = now;
        this.acceptedPriceCents = terms.priceCents();
        this.acceptedCoveragePct = terms.coveragePct();
        this.acceptedMaxCoverageCents = terms.maxCoverageCents();
        this.acceptedMinUserCashCents = terms.minUserCashCents();
        this.acceptedCommissionPct = terms.commissionPct();
        this.acceptedConfigVersion = terms.configVersion();
    }

    public void reject(ZonedDateTime now) {
        requirePending("rechazar");
        this.status = KeyCoveragePromotionOfferStatus.REJECTED;
        this.respondedAt = now;
    }

    /** Retiro posterior a la aceptación: solo rige hacia adelante. */
    public void withdraw(ZonedDateTime now) {
        if (status != KeyCoveragePromotionOfferStatus.ACCEPTED) {
            throw new InvalidStatusException("Solo se puede retirar una oferta aceptada (estado actual " + status + ")");
        }
        this.status = KeyCoveragePromotionOfferStatus.WITHDRAWN;
        this.respondedAt = now;
    }

    /** ¿Sigue ocupando el producto? Una oferta rechazada o retirada lo deja libre para otra promoción. */
    public boolean isOpen() {
        return status == KeyCoveragePromotionOfferStatus.PENDING_ACCEPTANCE
                || status == KeyCoveragePromotionOfferStatus.ACCEPTED;
    }

    private void requirePending(String action) {
        if (status != KeyCoveragePromotionOfferStatus.PENDING_ACCEPTANCE) {
            throw new InvalidStatusException("No se puede " + action + " una oferta en estado " + status);
        }
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
