package com.verygana2.models.products;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.exceptions.InsufficientStockException;
import com.verygana2.models.enums.DeliveryType;
import com.verygana2.models.enums.DigitalFormat;
import com.verygana2.models.userDetails.SellerDetails;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_seller_id", columnList = "seller_id"),
        @Index(name = "idx_product_category_id", columnList = "product_category_id"),
        @Index(name = "idx_price", columnList = "price"),
        @Index(name = "idx_average_rate", columnList = "average_rate"),
        @Index(name = "idx_active", columnList = "is_active")
})
@Data
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private SellerDetails seller;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL)
    private List<ProductReview> reviews = new ArrayList<>();

    @Column(name = "review_count")
    private Integer reviewCount;

    @Column(name = "average_rate")
    private Double averageRate;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "image_url", nullable = false, length = 500)
    private String imageUrl;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Transient
    @Column(nullable = false)
    private Integer stock;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductStock> stockItems = new ArrayList<>();

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_category_id", nullable = false)
    private ProductCategory productCategory;

    // Campos adicionales recomendados
    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", length = 20)
    private DeliveryType deliveryType; // AUTO, MANUAL, EXTERNAL_API

    @Enumerated(EnumType.STRING)
    @Column(name = "digital_format", length = 20)
    private DigitalFormat digitalFormat; // CODE, FILE, LINK, CREDENTIAL

    @Column(name = "is_instant_delivery")
    private boolean isInstantDelivery;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        averageRate = 0.0;
        reviewCount = 0;
        isActive = true;
        if (digitalFormat == DigitalFormat.CODE) {
            isInstantDelivery = true;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public String getImageUrl() {
        return this.imageUrl;
    }

    public Integer getAvailableStock(){
        return (int) stockItems.stream().filter(ProductStock::canBeSold).count();
    }

    public void updateStockCount(){
        this.stock = getAvailableStock();
    }

    public ProductStock getNextAvailableCode() {
        return stockItems.stream().filter(ProductStock::canBeSold).findFirst().orElseThrow(() -> new InsufficientStockException(this.name));
    }

    public void updateAverageRating() {
        List<ProductReview> visibleReviews = reviews.stream()
            .filter(ProductReview::isVisible)
            .toList();
        
        this.reviewCount = visibleReviews.size();
        
        if (reviewCount == 0) {
            this.averageRate = 0.0;
        } else {
            this.averageRate = visibleReviews.stream()
                .mapToInt(ProductReview::getRating)
                .average()
                .orElse(0.0);
        }
    }
}
