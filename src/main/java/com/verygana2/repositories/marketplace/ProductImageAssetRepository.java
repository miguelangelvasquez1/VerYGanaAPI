package com.verygana2.repositories.marketplace;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.marketplace.ProductImageAsset;

@Repository
public interface ProductImageAssetRepository extends JpaRepository<ProductImageAsset, Long> {
    Optional<ProductImageAsset> findByProductId (Long productId);

    @Query("""
        SELECT a FROM ProductImageAsset a
        WHERE a.status = :status
        AND a.uploadedAt < :threshold
    """)
    List<ProductImageAsset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );
}
