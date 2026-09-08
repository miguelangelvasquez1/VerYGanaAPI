package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Collection;
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

    /**
     * Assets subidos pero nunca vinculados a un anuncio (flujo de subida/análisis
     * abandonado) y más viejos que el umbral. Los que ya tienen {@code ad} nunca
     * se tocan.
     */
    @Query("""
        SELECT a FROM AdAsset a
        WHERE a.ad IS NULL
        AND a.status IN :statuses
        AND a.uploadedAt < :threshold
    """)
    List<AdAsset> findStaleUnattachedAssets(
        @Param("statuses") Collection<AssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
