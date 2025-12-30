package com.verygana2.repositories.games;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.dtos.game.GameDTO;
import com.verygana2.models.games.Game;

@Repository
public interface GameRepository extends JpaRepository<Game, Long> {
    
    Optional<Game> findByIdAndActiveTrue(Long id);

    @Query("""
        SELECT new com.verygana2.dtos.game.GameDTO(
            g.id,
            g.title,
            g.description,
            g.frontPageUrl
        )
        FROM Game g
        WHERE g.active = true
    """)
    Page<GameDTO> findAvailableGames(Pageable pageable);

    @Query("""
        SELECT new com.verygana2.dtos.game.GameDTO(
            g.id,
            g.title,
            g.description,
            g.frontPageUrl
        )
        FROM Game g
        WHERE g.active = true
        AND g.id NOT IN (
            SELECT c.game.id
            FROM Campaign c
            WHERE c.advertiser.id = :advertiserId
        )
    """)
    Page<GameDTO> findGamesWithoutCampaign(@Param("advertiserId") Long advertiserId, Pageable pageable);
}