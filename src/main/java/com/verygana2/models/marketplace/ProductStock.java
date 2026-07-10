package com.verygana2.models.marketplace;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.marketplace.StockStatus;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "product_stock",
    indexes = { 

        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_status", columnList = "status"),
        @Index(name = "idx_expiration_date_status", columnList = "expiration_date, status")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_code_hash_per_product", columnNames = {"product_id", "code_hash"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"product", "purchaseItem"})
@EqualsAndHashCode(exclude = {"product", "purchaseItem"})
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

    /**
     * Código/credencial/licencia cifrado con AES-GCM (CodeEncryptor). Nunca se
     * guarda en texto plano; para comparar igualdad o detectar duplicados usar
     * codeHash, no este campo.
     */
    @Column(nullable = false, length = 500)
    private String code;

    /**
     * HMAC-SHA256 (hex) del código en texto plano. Determinístico, no reversible.
     * Permite deduplicar/buscar coincidencias exactas sin descifrar codeHash.
     */
    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expiration_date")
    private ZonedDateTime expirationDate; // Si el código expira
    
    // ===== ESTADO =====
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StockStatus status = StockStatus.AVAILABLE;
    
    // ===== INFORMACIÓN DE VENTA =====
    
    @OneToOne(mappedBy = "assignedProductStock", fetch = FetchType.LAZY)
    private PurchaseItem purchaseItem; // A qué compra fue asignado
    
    @Column(name = "sold_at")
    private ZonedDateTime soldAt;
    
    // ===== AUDITORÍA =====
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
    
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
    
    // ===== MÉTODOS DE NEGOCIO =====
    
    public void markAsSold(PurchaseItem item) {
        this.status = StockStatus.SOLD;
        this.purchaseItem = item;
        this.soldAt = ZonedDateTime.now();
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
        return expirationDate != null && ZonedDateTime.now().isAfter(expirationDate);
    }
    
    public boolean canBeSold() {
        return status == StockStatus.AVAILABLE && !isExpired();
    }
}
