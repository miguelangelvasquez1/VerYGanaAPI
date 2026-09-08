// PrizeImageAssetRepository.java
package com.verygana2.repositories.raffles;

import java.time.ZonedDateTime;
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
}