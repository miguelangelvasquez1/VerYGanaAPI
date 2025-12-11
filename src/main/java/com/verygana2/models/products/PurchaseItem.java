package com.verygana2.models.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.verygana2.models.enums.PurchaseItemStatus;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "purchase_items")
@Data
@Builder
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
    
    @Column(nullable = false)
    private Integer quantity;
    
    // Precio al momento de la compra (importante para histórico)
    @Column(name = "price_at_purchase", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceAtPurchase;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;  // quantity * priceAtPurchase
    
    @Column(name = "delivered_code", columnDefinition = "TEXT")
    private String deliveredCode; // El código que se le entregó al cliente
    
    @Column(name = "delivered_credentials", columnDefinition = "TEXT")
    private String deliveredCredentials; // Credenciales entregadas (si aplica)
    
    @Column(name = "delivery_instructions", columnDefinition = "TEXT")
    private String deliveryInstructions; // Instrucciones de uso
    
    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt; // Cuándo se entregó

    @Transient
    public boolean hasReview() {
        return this.review != null;
    }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseItemStatus status = PurchaseItemStatus.PENDING;
    
    @PrePersist
    @PreUpdate
    protected void calculateSubtotal() {
        this.subtotal = priceAtPurchase.multiply(new BigDecimal(quantity));
    }

    public void assignProductStock(ProductStock stock) {
        this.assignedProductStock = stock;
        this.deliveredCode = stock.getCode();
        this.deliveredCredentials = stock.getAdditionalInfo();
        this.deliveredAt = LocalDateTime.now();
        this.status = PurchaseItemStatus.DELIVERED;
    }

    public boolean isDelivered() {
        return status == PurchaseItemStatus.DELIVERED;
    }

    public boolean canBeReviewed() {
        return this.isDelivered() && !this.hasReview();
    }
}
