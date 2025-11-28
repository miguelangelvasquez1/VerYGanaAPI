package com.verygana2.models.products;

import java.time.LocalDateTime;

import com.verygana2.models.enums.StockStatus;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_stock",
    indexes = { 

        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_status", columnList = "status"),
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_code", columnNames = "code")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductStock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;
    
    // ===== RELACIONES =====
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;
    
    // ===== INFORMACIÓN DEL CÓDIGO =====
    
    @Column(nullable = false, unique = true, length = 500)
    private String code; // El código/credencial/licencia
    
    @Column(name = "additional_info", columnDefinition = "TEXT")
    private String additionalInfo; // Ej: "Usuario: juan@example.com, Contraseña: xyz123"
    
    @Column(name = "expiration_date")
    private LocalDateTime expirationDate; // Si el código expira
    
    // ===== ESTADO =====
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StockStatus status = StockStatus.AVAILABLE;
    
    // ===== INFORMACIÓN DE VENTA =====
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_item_id")
    private PurchaseItem purchaseItem; // A qué compra fue asignado
    
    @Column(name = "sold_at")
    private LocalDateTime soldAt;
    
    // ===== AUDITORÍA =====
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ===== MÉTODOS DE NEGOCIO =====
    
    public void markAsSold(PurchaseItem item) {
        this.status = StockStatus.SOLD;
        this.purchaseItem = item;
        this.soldAt = LocalDateTime.now();
    }
    
    public void markAsReserved() {
        this.status = StockStatus.RESERVED;
    }
    
    public void markAsAvailable() {
        this.status = StockStatus.AVAILABLE;
        this.purchaseItem = null;
        this.soldAt = null;
    }
    
    public boolean isExpired() {
        return expirationDate != null && LocalDateTime.now().isAfter(expirationDate);
    }
    
    public boolean canBeSold() {
        return status == StockStatus.AVAILABLE && !isExpired();
    }
}
