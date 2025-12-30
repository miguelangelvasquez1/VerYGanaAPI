package com.verygana2.repositories.games;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.models.games.GameAssetDefinition;

public interface GameAssetDefinitionRepository extends JpaRepository<GameAssetDefinition, Long> {

    @Query("""
        SELECT DISTINCT gad
        FROM GameAssetDefinition gad
        LEFT JOIN FETCH gad.allowedMimeTypes
        WHERE gad.game.id = :gameId
        ORDER BY gad.id
    """)
    List<GameAssetDefinition> findByGameIdWithMimeTypes(
            @Param("gameId") Long gameId
    );


    List<GameAssetDefinition> findByGameId(Long gameId);
}
