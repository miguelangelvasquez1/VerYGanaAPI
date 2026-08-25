// RaffleImageAssetRepository.java
package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
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
}