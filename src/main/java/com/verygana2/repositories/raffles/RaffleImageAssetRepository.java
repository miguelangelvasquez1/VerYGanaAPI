// RaffleImageAssetRepository.java
package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.raffles.RaffleImageAsset;

public interface RaffleImageAssetRepository extends JpaRepository<RaffleImageAsset, Long> {
    Optional<RaffleImageAsset> findByRaffleId(Long raffleId);

    @Query("""
        SELECT a FROM RaffleImageAsset a
        WHERE a.status = :status
        AND a.uploadedAt < :threshold
    """)
    List<RaffleImageAsset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );

    /**
     * PENDING (u otros estados dados) nunca vinculados a una rifa y más viejos que el umbral:
     * flujos de subida abandonados. Los ya vinculados nunca se devuelven, así que un archivo
     * en uso no puede entrar al barrido.
     */
    @Query("""
        SELECT a FROM RaffleImageAsset a
        WHERE a.raffle IS NULL
        AND a.status IN :statuses
        AND a.uploadedAt < :threshold
    """)
    List<RaffleImageAsset> findStaleUnattachedAssets(
        @Param("statuses") Collection<AssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
