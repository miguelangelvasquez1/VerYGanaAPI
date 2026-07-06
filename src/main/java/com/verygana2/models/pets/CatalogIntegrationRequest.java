package com.verygana2.models.pets;

import com.verygana2.models.enums.CatalogRequestStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "catalog_integration_requests")
@Data
@NoArgsConstructor
public class CatalogIntegrationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @Column(name = "product_name", nullable = false, length = 100)
    private String productName;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(name = "image_object_key", length = 300)
    private String imageObjectKey;

    @Column(name = "desired_effects", nullable = false, length = 1000)
    private String desiredEffects;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogRequestStatus status = CatalogRequestStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "result_catalog_item_id")
    private Long resultCatalogItemId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}