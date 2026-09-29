package com.verygana2.repositories.marketplace;

import java.time.ZonedDateTime;
import java.util.Collection;
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

    /**
     * PENDING (u otros estados dados) nunca vinculados a un producto y más viejos que el umbral:
     * flujos de subida abandonados. Los ya vinculados nunca se devuelven, así que un archivo
     * en uso no puede entrar al barrido.
     */
    @Query("""
        SELECT a FROM ProductImageAsset a
        WHERE a.product IS NULL
        AND a.status IN :statuses
        AND a.uploadedAt < :threshold
    """)
    List<ProductImageAsset> findStaleUnattachedAssets(
        @Param("statuses") Collection<AssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
