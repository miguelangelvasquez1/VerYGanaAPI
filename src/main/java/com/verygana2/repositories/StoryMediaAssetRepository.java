package com.verygana2.repositories;

import java.time.ZonedDateTime;
import java.util.Collection;
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

    /**
     * PENDING (u otros estados dados) nunca vinculados a una historia y más viejos que el umbral:
     * flujos de subida abandonados. Los ya vinculados nunca se devuelven, así que un archivo
     * en uso no puede entrar al barrido.
     */
    @Query("""
        SELECT a FROM StoryMediaAsset a
        WHERE a.impactStory IS NULL
        AND a.status IN :statuses
        AND a.createdAt < :threshold
    """)
    List<StoryMediaAsset> findStaleUnattachedAssets(
        @Param("statuses") Collection<StoryMediaAsset.MediaAssetStatus> statuses,
        @Param("threshold") ZonedDateTime threshold
    );
}
