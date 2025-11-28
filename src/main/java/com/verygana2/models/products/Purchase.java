package com.verygana2.models.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


import com.verygana2.models.enums.PurchaseStatus;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "purchases", indexes = {
    @Index(name = "idx_consumer_id", columnList = "consumer_id"),
    @Index(name = "idx_purchase_status", columnList = "status"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Purchase {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "reference_id", nullable = false, updatable = false)
    private String referenceId;
    
    // ===== RELACIONES =====
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;
    
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();
    
    // ===== INFORMACIÓN FINANCIERA =====
    
    @Column(name = "subtotal", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotal;
    
    @Column(name = "discount_amount", precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    @Column(name = "total", nullable = false, precision = 19, scale = 2)
    private BigDecimal total;

    @Column(name = "platform_earnings", nullable = false)
    private BigDecimal platformEarnings;

    @Column(name = "paid_to_sellers", nullable = false)
    private BigDecimal paidToSellers;
    
    // ===== ESTADO DE LA COMPRA =====
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.PENDING;
    
    // ===== INFORMACIÓN DE ENTREGA DIGITAL =====
    
    @Column(name = "contact_email", length = 255)
    private String contactEmail; // Email donde se envían las credenciales
    
    @Column(name = "credentials_sent_at")
    private LocalDateTime credentialsSentAt; // Cuándo se enviaron las credenciales
    
    // ===== CUPONES Y DESCUENTOS =====
    
    @Column(name = "coupon_code", length = 50)
    private String couponCode;
    
    // ===== NOTAS =====
    
    @Column(columnDefinition = "TEXT")
    private String notes; // Notas del comprador
    
    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes; // Notas internas del admin/seller
    
    // ===== AUDITORÍA =====
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt; // Cuándo se completó la compra
    
    // ===== MÉTODOS DE CICLO DE VIDA =====
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ===== MÉTODOS DE NEGOCIO =====
    
    public void addItem(PurchaseItem item) {
        items.add(item);
        item.setPurchase(this);
    }
    
    public void removeItem(PurchaseItem item) {
        items.remove(item);
        item.setPurchase(null);
    }
    
    public void calculateTotals() {
        this.subtotal = items.stream()
            .map(PurchaseItem::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        this.total = subtotal.subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO);
    }
    
    public void markAsCompleted() {
        this.status = PurchaseStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.credentialsSentAt = LocalDateTime.now();
    }

    public void updatePlatformEarnings(BigDecimal newEarning){
        this.platformEarnings = platformEarnings.add(newEarning);
    }

    public void updatePaidToSellers(BigDecimal newPaidToSellers){
        this.paidToSellers = paidToSellers.add(newPaidToSellers);
    }
}