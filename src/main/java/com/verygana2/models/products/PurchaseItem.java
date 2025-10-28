package com.verygana2.models.products;

import java.math.BigDecimal;

import com.verygana2.models.enums.PurchaseItemStatus;
import com.verygana2.models.userDetails.SellerDetails;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "purchase_items")
@Data
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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerDetails seller;  // Para saber a quién pagar
    
    @Column(nullable = false)
    private Integer quantity;
    
    // Precio al momento de la compra (importante para histórico)
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceAtPurchase;
    
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;  // quantity * priceAtPurchase
    
    // Información del producto al momento de compra (por si se elimina después)
    @Column(nullable = false)
    private String productName;
    
    @Column
    private String productImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseItemStatus status = PurchaseItemStatus.PENDING;
    
    @PrePersist
    @PreUpdate
    protected void calculateSubtotal() {
        this.subtotal = priceAtPurchase.multiply(new BigDecimal(quantity));
    }
}
