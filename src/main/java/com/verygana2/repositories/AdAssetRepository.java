package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.ads.AdAsset;
import com.verygana2.models.enums.AssetStatus;

@Repository
public interface AdAssetRepository extends JpaRepository<AdAsset, Long> {

    /**
     * Obtener asset por object key
     */
    Optional<AdAsset> findByObjectKey(String objectKey);

    /**
     * Obtener assets huérfanos para limpieza
     */
    List<AdAsset> findByStatus(AssetStatus status);

    @Query("""
        SELECT a FROM AdAsset a
        WHERE a.status = :status
        AND a.uploadedAt < :threshold
    """)
    List<AdAsset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );
}