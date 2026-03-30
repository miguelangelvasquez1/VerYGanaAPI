package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.ImpactStory.StoryMediaAsset;

@Repository
public interface StoryMediaAssetRepository extends JpaRepository<StoryMediaAsset, Long> {
    
    @Query("""
        SELECT a FROM StoryMediaAsset a
        WHERE a.status = :status
        AND a.createdAt < :threshold
    """)
    List<StoryMediaAsset> findDeletableAssets(
        @Param("status") StoryMediaAsset.MediaAssetStatus status,
        @Param("threshold") ZonedDateTime threshold
    );
}
