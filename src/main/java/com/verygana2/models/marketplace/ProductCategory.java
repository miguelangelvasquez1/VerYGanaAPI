package com.verygana2.models.marketplace;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.AdminDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Entity
@Data
public class ProductCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    private AdminDetails createdBy;

    @OneToOne(mappedBy = "productCategory", fetch = FetchType.LAZY)
    private ProductCategoryImageAsset imageAsset;

    @PrePersist
    protected void onCreate() {
        createdAt = ZonedDateTime.now(ZoneOffset.UTC);
        isActive = true;
    }

    public String getImageUrl () {
        if (this.imageAsset == null) return null;
        return "https://cdn.verygana.com/public/" + this.imageAsset.getObjectKey();
    }
}
