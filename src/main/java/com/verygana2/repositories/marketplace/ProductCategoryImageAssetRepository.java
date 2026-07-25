package com.verygana2.repositories.marketplace;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.marketplace.ProductCategoryImageAsset;

public interface ProductCategoryImageAssetRepository extends JpaRepository <ProductCategoryImageAsset, Long>{

    @Query("""
        SELECT a FROM ProductCategoryImageAsset a
        WHERE a.status = :status
        AND a.uploadedAt < :threshold
    """)
    List<ProductCategoryImageAsset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );
}
