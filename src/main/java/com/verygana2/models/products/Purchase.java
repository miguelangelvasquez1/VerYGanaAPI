package com.verygana2.models.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.verygana2.models.enums.products.PaymentMethod;
import com.verygana2.models.enums.products.PurchaseStatus;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "purchases")
@Data
public class Purchase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, updatable = false)
    private String referenceId;

    // ===== RELACIONES =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseItem> items = new ArrayList<>();

    // ===== INFORMACIÓN DE LA COMPRA =====

    @Column(nullable = false)
    private ZonedDateTime purchaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseStatus status; // PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED, REFUNDED

    // ===== MONTOS (todos calculados pero almacenados para histórico) =====

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal; // Suma de productos sin impuestos

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal taxAmount; // IVA u otros impuestos

    @Column(precision = 10, scale = 2)
    private BigDecimal discount; // Descuentos aplicados (cupones, promociones)

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount; // Total final pagado

    // ===== INFORMACIÓN DE ENTREGA =====

    @Column(nullable = false)
    private String deliveryAddress;

    @Column(nullable = false)
    private String deliveryCity;

    @Column(nullable = false)
    private String deliveryDepartment;

    @Column
    private String deliveryPhone;

    @Column
    private String deliveryNotes; // Instrucciones especiales de entrega Ej: "Horario disponible 2-5pm"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod paymentMethod; // Siempre será WALLET por ahora
    // ===== TRACKING =====

    @Column
    private LocalDateTime cancelledDate;

    @Column
    private String cancellationReason;

    // ===== AUDITORÍA =====

    @Column(updatable = false)
    private ZonedDateTime createdAt;

    @Column
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (referenceId == null) {
            referenceId = "PURCHASE-" + UUID.randomUUID().toString();
        }
        createdAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        if (purchaseDate == null) {
            purchaseDate = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
    }

    // ===== MÉTODOS DE NEGOCIO =====

    public void calculateTotals() {
        this.subtotal = items.stream()
                .map(PurchaseItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // IVA 19% en Colombia (ajustar según tu país)
        this.taxAmount = subtotal.multiply(new BigDecimal("0.19"));

        this.totalAmount = subtotal
                .add(taxAmount)
                .subtract(discount != null ? discount : BigDecimal.ZERO);
    }

    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setPurchase(this);
        calculateTotals();
    }

    public void removeItem(PurchaseItem item) {
        items.remove(item);
        item.setPurchase(null);
        calculateTotals();
    }
}
