package com.verygana2.models.marketplace.promotions;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.exceptions.BusinessException;
import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.enums.marketplace.KeyCoveragePromotionStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Promoción especial de cobertura con llaves (Contrato B 6.22.8, 7.9.2): sube el tope del
 * 50 % ordinario hasta un máximo del 70 %, solo para las ofertas que cada empresario
 * aceptó incorporar, dentro de una vigencia y con un presupuesto.
 *
 * El presupuesto mide el EXCESO: lo que los usuarios cubren con llaves por encima del
 * tope ordinario. Se reserva al crear la compra, se consume al aprobarse el pago y se
 * libera si el pago falla. Solo se modifica con la fila bloqueada.
 */
@Entity
@Table(name = "key_coverage_promotions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeyCoveragePromotion {

    public static final int ORDINARY_MAX_PCT = 50;
    public static final int SPECIAL_MAX_PCT = 70;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "coverage_pct", nullable = false)
    private Integer coveragePct;

    @Column(name = "starts_at", nullable = false)
    private ZonedDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private ZonedDateTime endsAt;

    @Column(name = "budget_cents", nullable = false)
    private Long budgetCents;

    /** Exceso comprometido por compras con pago en curso. */
    @Column(name = "reserved_cents", nullable = false)
    private Long reservedCents = 0L;

    /** Exceso de compras ya aprobadas. */
    @Column(name = "consumed_cents", nullable = false)
    private Long consumedCents = 0L;

    /** Lo que el Fondo ya puso por ventas aprobadas (repuesto a KEYS_RESERVE); no vuelve a FORTIFICATION. */
    @Column(name = "guarantee_drawn_cents", nullable = false)
    private Long guaranteeDrawnCents = 0L;

    /** Lo que ya volvió a FORTIFICATION tras el cierre. Puede devolverse por partes. */
    @Column(name = "guarantee_returned_cents", nullable = false)
    private Long guaranteeReturnedCents = 0L;

    /** Cuándo terminó de devolverse el fondo: cerrada y sin compras con pago en curso. */
    @Column(name = "guarantee_returned_at")
    private ZonedDateTime guaranteeReturnedAt;

    /** Tope de exceso por usuario durante toda la promoción. Null = sin tope. */
    @Column(name = "max_excess_cents_per_user")
    private Long maxExcessCentsPerUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private KeyCoveragePromotionStatus status;

    /** Sube con cada edición del borrador y queda fija al autorizar: es lo que cada empresario acepta. */
    @Column(name = "config_version", nullable = false)
    private Integer configVersion;

    @Column(name = "terms_text", columnDefinition = "TEXT")
    private String termsText;

    @Column(name = "authorized_by_user_id")
    private Long authorizedByUserId;

    @Column(name = "authorized_at")
    private ZonedDateTime authorizedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    /** Lo que define una promoción mientras es borrador. */
    public record Terms(
            String name,
            int coveragePct,
            ZonedDateTime startsAt,
            ZonedDateTime endsAt,
            long budgetCents,
            Long maxExcessCentsPerUser,
            String termsText) {
    }

    public static KeyCoveragePromotion draft(Terms terms) {
        KeyCoveragePromotion promotion = new KeyCoveragePromotion();
        promotion.status = KeyCoveragePromotionStatus.DRAFT;
        promotion.configVersion = 0;
        promotion.applyTerms(terms);
        return promotion;
    }

    /** Solo un borrador se edita: lo autorizado es lo que los empresarios aceptaron. */
    public void updateDraft(Terms terms) {
        requireStatus(KeyCoveragePromotionStatus.DRAFT, "editar");
        applyTerms(terms);
    }

    private void applyTerms(Terms terms) {
        if (terms.coveragePct() <= ORDINARY_MAX_PCT || terms.coveragePct() > SPECIAL_MAX_PCT) {
            throw new BusinessException("El porcentaje de una promoción especial debe ser mayor a "
                    + ORDINARY_MAX_PCT + " % y no superar el " + SPECIAL_MAX_PCT + " %");
        }
        if (terms.startsAt() == null || terms.endsAt() == null || !terms.endsAt().isAfter(terms.startsAt())) {
            throw new BusinessException("La promoción necesita fecha de inicio y una fecha de fin posterior");
        }
        if (terms.budgetCents() <= 0) {
            throw new BusinessException("El presupuesto de la promoción debe ser positivo");
        }
        if (terms.maxExcessCentsPerUser() != null && terms.maxExcessCentsPerUser() <= 0) {
            throw new BusinessException("El límite por usuario debe ser positivo");
        }
        if (terms.maxExcessCentsPerUser() != null && terms.maxExcessCentsPerUser() > terms.budgetCents()) {
            throw new BusinessException("El límite por usuario no puede ser mayor que el presupuesto de la promoción");
        }
        this.name = terms.name();
        this.coveragePct = terms.coveragePct();
        this.startsAt = terms.startsAt();
        this.endsAt = terms.endsAt();
        this.budgetCents = terms.budgetCents();
        this.maxExcessCentsPerUser = terms.maxExcessCentsPerUser();
        this.termsText = terms.termsText();
        this.configVersion = this.configVersion + 1;
    }

    public void authorize(Long adminUserId, ZonedDateTime now) {
        requireStatus(KeyCoveragePromotionStatus.DRAFT, "autorizar");
        if (!endsAt.isAfter(now)) {
            throw new BusinessException("No se puede autorizar una promoción cuya vigencia ya terminó");
        }
        this.status = KeyCoveragePromotionStatus.AUTHORIZED;
        this.authorizedByUserId = adminUserId;
        this.authorizedAt = now;
    }

    public void pause() {
        requireStatus(KeyCoveragePromotionStatus.AUTHORIZED, "pausar");
        this.status = KeyCoveragePromotionStatus.PAUSED;
    }

    public void resume() {
        requireStatus(KeyCoveragePromotionStatus.PAUSED, "reanudar");
        this.status = KeyCoveragePromotionStatus.AUTHORIZED;
    }

    public void end() {
        if (status == KeyCoveragePromotionStatus.ENDED) {
            return;
        }
        this.status = KeyCoveragePromotionStatus.ENDED;
    }

    /** ¿Aplica a una compra hecha en este instante? Ni automática ni permanente (7.9.2). */
    public boolean isLiveAt(ZonedDateTime instant) {
        return status == KeyCoveragePromotionStatus.AUTHORIZED
                && !instant.isBefore(startsAt)
                && instant.isBefore(endsAt);
    }

    public boolean hasGuarantee() {
        return authorizedAt != null && guaranteeReturnedAt == null;
    }

    public long availableBudgetCents() {
        return budgetCents - reservedCents - consumedCents;
    }

    /**
     * ¿Le queda presupuesto para subir el tope de una unidad de este producto? El margen
     * de una unidad es lo que la promoción cubre por encima del tope ordinario. Sin
     * presupuesto para eso la promoción ya no aplica a ese producto, aunque siga vigente.
     *
     * @param keyValueCents valor de una llave: la cobertura va en llaves enteras.
     */
    public boolean canCoverOneUnit(long priceCents, int productOrdinaryPct, long keyValueCents) {
        int ordinaryPct = Math.min(productOrdinaryPct, ORDINARY_MAX_PCT);
        long ordinaryLimit = priceCents * ordinaryPct / 100 / keyValueCents * keyValueCents;
        long promotionLimit = priceCents * coveragePct / 100 / keyValueCents * keyValueCents;
        long excess = promotionLimit - ordinaryLimit;
        return excess > 0 && excess <= availableBudgetCents();
    }

    public void reserve(long excessCents) {
        if (excessCents > availableBudgetCents()) {
            throw new BusinessException("La promoción \"" + name + "\" agotó su presupuesto");
        }
        this.reservedCents += excessCents;
    }

    public void confirm(long excessCents) {
        requireReserved(excessCents);
        this.reservedCents -= excessCents;
        this.consumedCents += excessCents;
    }

    public void release(long excessCents) {
        requireReserved(excessCents);
        this.reservedCents -= excessCents;
    }

    /**
     * Reembolso de una venta ya aprobada: el exceso deja de contar como consumido (el
     * presupuesto vuelve a estar disponible) y lo que el Fondo había puesto por ella deja
     * de contar como aportado.
     *
     * @return lo que el Fondo había puesto por ese exceso y hay que devolverle. Puede ser
     *         menos que el exceso en ventas aprobadas antes de que el Fondo lo asumiera.
     */
    public long restore(long excessCents) {
        if (excessCents > consumedCents) {
            throw new IllegalStateException("La promoción " + id + " no tiene " + excessCents
                    + " centavos consumidos (consumido " + consumedCents + ")");
        }
        long funded = Math.min(excessCents, guaranteeDrawnCents);
        this.consumedCents -= excessCents;
        this.guaranteeDrawnCents -= funded;
        return funded;
    }

    public void registerGuaranteeDrawn(long cents) {
        this.guaranteeDrawnCents += cents;
    }

    /**
     * Lo que puede volver a FORTIFICATION ahora: del presupuesto apartado, lo que el Fondo
     * no ha puesto por ventas, no está comprometido por compras con pago en curso y no se
     * ha devuelto ya. Solo tiene sentido con la promoción cerrada.
     */
    public long releasableGuaranteeCents() {
        return Math.max(0L, budgetCents - guaranteeDrawnCents - reservedCents - guaranteeReturnedCents);
    }

    /**
     * Registra una devolución a FORTIFICATION. Si ya no quedan compras con pago en curso,
     * el fondo de la promoción queda saldado.
     */
    public void registerGuaranteeReturned(long cents, ZonedDateTime now) {
        if (status != KeyCoveragePromotionStatus.ENDED) {
            throw new InvalidStatusException("El fondo de una promoción solo se devuelve cuando está cerrada");
        }
        this.guaranteeReturnedCents += cents;
        if (reservedCents == 0) {
            this.guaranteeReturnedAt = now;
        }
    }

    private void requireReserved(long excessCents) {
        if (excessCents > reservedCents) {
            throw new IllegalStateException("La promoción " + id + " no tiene " + excessCents
                    + " centavos reservados (reservado " + reservedCents + ")");
        }
    }

    private void requireStatus(KeyCoveragePromotionStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStatusException(
                    "No se puede " + action + " una promoción en estado " + status);
        }
    }

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
