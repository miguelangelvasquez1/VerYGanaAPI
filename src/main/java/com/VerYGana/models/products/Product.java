package com.VerYGana.models.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_seller_id", columnList = "seller_id"),
    @Index(name = "idx_category_id", columnList = "category_id"),
    @Index(name = "idx_price", columnList = "price"),
    @Index(name = "idx_average_rate", columnList = "average_rate"),
    @Index(name = "idx_active", columnList = "is_active")
})
@Data
public class Product {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(name = "images_urls", nullable = false, length = 500)
    private List<String> imagesUrls;
    
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    
    @Column(nullable = false)
    private Integer stock;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private ProductCategory category;
    

    @Column(name = "average_rate", nullable = false, columnDefinition = "DECIMAL(3,2) DEFAULT 0.00")
    private Double averageRate;
    
    // Campos adicionales recomendados
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (averageRate == null) {
            averageRate = 0.0;
        }
        isActive = true;
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
