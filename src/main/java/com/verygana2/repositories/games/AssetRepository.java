package com.verygana2.repositories.games;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.enums.AssetStatus;
import com.verygana2.models.games.Asset;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    
    List<Asset> findAllByIdInAndStatus(List<Long> ids, AssetStatus status);

    @Query("""
        SELECT a FROM Asset a
        WHERE a.status = :status
        AND a.createdAt < :threshold
    """)
    List<Asset> findDeletableAssets(
        @Param("status") AssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );

    List<Asset> findByCampaignId(Long campaignId);
}