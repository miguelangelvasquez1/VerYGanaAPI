package com.verygana2.models.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.hibernate.annotations.Immutable;

import com.verygana2.models.enums.marketplace.SaleComponentStatus;
import com.verygana2.models.enums.marketplace.SaleComponentType;
import com.verygana2.models.enums.marketplace.SalePayerType;

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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Un componente económico del precio reconocido de un ítem (Contrato B 5.9): quién lo
 * aporta, cuánto y de dónde sale ({@code sourceRef}: el copago o el fundingRef del
 * reconocimiento). Cada componente tiene identidad propia para que su compromiso,
 * ejecución y pago se relacionen sin contarse dos veces.
 *
 * Append-only: una anulación es otro componente REVERSED que apunta al original.
 */
@Entity
@Immutable
@Table(name = "sale_components")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SaleComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_item_id", nullable = false, updatable = false)
    private PurchaseItem purchaseItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "component_type", nullable = false, updatable = false, length = 20)
    private SaleComponentType componentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "payer_type", nullable = false, updatable = false, length = 20)
    private SalePayerType payerType;

    @Column(name = "source_ref", nullable = false, updatable = false, length = 64)
    private String sourceRef;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private Long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, updatable = false, length = 20)
    private SaleComponentStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reverses_component_id", updatable = false)
    private SaleComponent reversesComponent;

    @Column(name = "cause", length = 500, updatable = false)
    private String cause;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    public static SaleComponent recognized(PurchaseItem item, SaleComponentType type, SalePayerType payer,
            String sourceRef, long amountCents) {
        if (amountCents <= 0) {
            throw new IllegalArgumentException("Un componente de venta debe tener valor positivo: " + amountCents);
        }
        SaleComponent component = new SaleComponent();
        component.purchaseItem = item;
        component.componentType = type;
        component.payerType = payer;
        component.sourceRef = sourceRef;
        component.amountCents = amountCents;
        component.status = SaleComponentStatus.RECOGNIZED;
        return component;
    }

    /** Asiento compensatorio: mismo tipo, aportante, origen y valor que el original. */
    public SaleComponent reversal(String cause) {
        if (status != SaleComponentStatus.RECOGNIZED) {
            throw new IllegalStateException("Solo se reversa un componente reconocido: " + id);
        }
        SaleComponent reversal = new SaleComponent();
        reversal.purchaseItem = purchaseItem;
        reversal.componentType = componentType;
        reversal.payerType = payerType;
        reversal.sourceRef = sourceRef;
        reversal.amountCents = amountCents;
        reversal.status = SaleComponentStatus.REVERSED;
        reversal.reversesComponent = this;
        reversal.cause = cause;
        return reversal;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = ZonedDateTime.now(ZoneOffset.UTC);
    }
}
