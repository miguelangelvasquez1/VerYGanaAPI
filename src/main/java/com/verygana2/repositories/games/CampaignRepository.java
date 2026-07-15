package com.verygana2.repositories.games;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.verygana2.models.Municipality;
import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.CampaignStatus;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    long countByCommercialIdAndStatus(Long commercialId, CampaignStatus status);

    long countByCommercialIdAndStatusNotIn(Long commercialId, List<CampaignStatus> excludedStatuses);

    List<Campaign> findByCommercialId(Long commercialId);

    Optional<Campaign> findByIdAndCommercialId(Long id, Long commercialId);

    boolean existsByCommercialIdAndGameId(Long commercialId, Long gameId);

    /**
     * Retorna campañas candidatas (de cualquier juego) que pasan todos los hard filters.
     * No se restringe a un juego en particular: el juego a jugar es una consecuencia de la
     * campaña elegida, no un filtro de entrada — {@code CampaignScorer} decide, vía scoring
     * ponderado, cuál se ajusta mejor al consumidor (ver {@code AdRepository.findEligibleAdsForConsumer}
     * para el análogo de anuncios).
     *
     * <p>Hard filters aplicados:
     * <ul>
     *   <li>status = ACTIVE, juego activo, presupuesto no agotado (spentCents &lt; budgetCents)</li>
     *   <li>Municipio: si la campaña tiene municipios objetivo, el consumidor debe pertenecer a uno</li>
     *   <li>Límite diario: si maxSessionsPerUserPerDay está definido, el consumidor no puede haberlo superado hoy</li>
     * </ul>
     */
    @Query("""
           SELECT c FROM Campaign c
           WHERE c.status = :status
           AND c.game.active = true
           AND c.spentCents < c.budgetCents

           AND (:municipality IS NULL
                OR c.targetAudience IS NULL
                OR c.targetAudience.targetMunicipalities IS EMPTY
                OR :municipality MEMBER OF c.targetAudience.targetMunicipalities)

           AND (c.maxSessionsPerUserPerDay IS NULL OR (
                  SELECT COUNT(gs) FROM GameSession gs
                  WHERE gs.campaign.id = c.id
                  AND gs.consumer.id = :consumerId
                  AND gs.startTime >= :todayStart
           ) < c.maxSessionsPerUserPerDay)
    """)
    List<Campaign> findEligibleCampaignsForConsumer(
           @Param("consumerId") Long consumerId,
           @Param("status") CampaignStatus status,
           @Param("municipality") Municipality municipality,
           @Param("todayStart") ZonedDateTime todayStart,
           Pageable pageable
    );
}
