package com.verygana2.repositories.games;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO;
import com.verygana2.models.games.GameAssetDefinition;

public interface GameAssetDefinitionRepository extends JpaRepository<GameAssetDefinition, Long> {

    @Query("""
        SELECT new com.verygana2.dtos.game.campaign.GameAssetDefinitionDTO(
            gad.id,
            gad.assetType,
            gad.mediaType,
            gad.required,
            gad.multiple,
            gad.description
        )
        FROM GameAssetDefinition gad
        WHERE gad.game.id = :gameId
        ORDER BY gad.id
    """)
    List<GameAssetDefinitionDTO> findDtosByGameId(
            @Param("gameId") Long gameId
    );

    List<GameAssetDefinition> findByGameId(Long gameId);
}
