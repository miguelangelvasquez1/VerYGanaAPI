// PrizeImageAssetRepository.java
package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.raffles.PrizeImageAsset;

public interface PrizeImageAssetRepository extends JpaRepository<PrizeImageAsset, Long> {

    @Query("""
        SELECT a FROM PrizeImageAsset a
        WHERE a.status = :status
        AND a.uploadedAt < :threshold
    """)
    List<PrizeImageAsset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );

    /**
     * PENDING (u otros estados dados) nunca vinculados a un premio y más viejos que el umbral:
     * flujos de subida abandonados. Los ya vinculados nunca se devuelven, así que un archivo
     * en uso no puede entrar al barrido.
     */
    @Query("""
        SELECT a FROM PrizeImageAsset a
        WHERE a.prize IS NULL
        AND a.status IN :statuses
        AND a.uploadedAt < :threshold
    """)
    List<PrizeImageAsset> findStaleUnattachedAssets(
        @Param("statuses") Collection<AssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
