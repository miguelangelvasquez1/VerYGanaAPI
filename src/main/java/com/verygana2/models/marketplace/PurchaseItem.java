package com.verygana2.models.marketplace;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.marketplace.PurchaseItemStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "purchase_items", indexes = {
        @Index(name = "idx_purchase_items_purchase_id", columnList = "purchase_id"),
        @Index(name = "idx_purchase_items_product_id", columnList = "product_id"),
        @Index(name = "idx_purchase_items_delivered_at", columnList = "delivered_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = { "purchase", "product", "assignedProductStock", "review" })
@EqualsAndHashCode(exclude = { "purchase", "product", "assignedProductStock", "review" })
public class PurchaseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_id", nullable = false)
    private Purchase purchase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_stock_id")
    private ProductStock assignedProductStock;

    @OneToOne(mappedBy = "purchaseItem", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private ProductReview review;

    /**
     * Precio unitario del producto en centavos de COP en el momento de la compra.
     * Snapshot inmutable: si el empresario cambia el precio después, este registro
     * muestra lo que el usuario pagó realmente.
     *
     * Es igual a Product.priceCents en el momento de crear el PurchaseItem.
     * Se asigna en PurchaseService.create(), nunca en un hook @PrePersist.
     */

    @Column(name = "unit_price_cents", nullable = false)
    private Long unitPriceCents;

    /**
     * Subtotal de este ítem en centavos.
     * En esta plataforma siempre es igual a unitPriceCents porque cada
     * PurchaseItem representa exactamente 1 unidad de 1 producto digital.
     *
     * POR QUÉ no hay campo `quantity`:
     * Los productos son códigos digitales únicos (cada ProductStock es irrepetible).
     * Si el usuario quiere 3 unidades del mismo producto, se crean 3 PurchaseItem,
     * cada uno con su propio ProductStock asignado. Esto simplifica la lógica de
     * asignación de stock y evita tener que dividir subtotales al hacer el payout.
     *
     * Se calcula en PurchaseService antes de persistir: subtotalCents = unitPriceCents.
     */
    @Column(name = "subtotal_cents", nullable = false)
    private Long subtotalCents;

    /**
     * Porcentaje de comisión aplicado sobre este ítem específico.
     * Snapshot calculado en PurchaseService al momento de crear el ítem:
     *
     *   - Si commercial.investment.roiReached = false → 0 (aún no alcanzó 6× inversión)
     *   - Si plan = BASIC                             → ~30 (comisión alta desde el inicio)
     *   - Si plan = STANDARD/PREMIUM y roiReached     → 10
     *
     * Se persiste aquí porque cada ítem puede pertenecer a un comercial distinto
     * con su propio plan. Guardar el porcentaje por ítem hace que
     * el historial sea autocontenido y auditable individualmente.
     */
    @Column(name = "commission_pct_applied", nullable = false)
    @Builder.Default
    private Integer commissionPctApplied = 0;

    /**
     * Comisión en centavos retenida por VeryGana sobre este ítem.
     * = subtotalCents × commissionPctApplied / 100
     *
     * La suma de este campo en todos los ítems de una Purchase
     * es igual a Purchase.commissionCents.
     *
     * La suma de este campo en todos los ítems de un empresario en un período
     * alimenta Payout.commissionCents del job diario.
     */
    @Column(name = "commission_cents", nullable = false)
    @Builder.Default
    private Long commissionCents = 0L;

    /**
     * Lo que le corresponde al empresario después de comisión.
     * = totalCents - commissionCents.
     * Este es el monto que eventualmente sale de PAYOUTS_PENDING hacia el empresario.
     */
    @Column(name = "net_to_commercial_cents", nullable = false)
    @Builder.Default
    private Long netToCommercialCents = 0L;

    /**
     * Porcentaje máximo de llaves que el plan del empresario permite para este producto.
     * Snapshot de Product.maxKeysPct en el momento de la compra.
     * Se persiste aquí para que el histórico de la compra sea autocontenido.
     */
    @Column(name = "max_keys_pct_at_purchase", nullable = false)
    private Integer maxKeysPctAtPurchase;

    @Column(name = "delivered_code", columnDefinition = "TEXT")
    private String deliveredCode; // El código que se le entregó al cliente

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt; // Cuándo se entregó

     @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseItemStatus status = PurchaseItemStatus.PENDING;


    public void assignProductStock(ProductStock stock) {
        this.assignedProductStock = stock;
        this.deliveredCode = stock.getCode();
        this.deliveredAt = ZonedDateTime.now();
        this.status = PurchaseItemStatus.DELIVERED;
    }

    public boolean isDelivered() {
        return status == PurchaseItemStatus.DELIVERED;
    }

    @Transient
    public boolean hasReview() {
        return this.review != null;
    }

    public boolean canBeReviewed() {
        return this.isDelivered();
    }
}
