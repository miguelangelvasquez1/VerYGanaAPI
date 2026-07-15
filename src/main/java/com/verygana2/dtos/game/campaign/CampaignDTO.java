package com.verygana2.dtos.game.campaign;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.models.Category;
import com.verygana2.models.enums.CampaignGoal;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.TargetGender;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CampaignDTO {

    private Long id;

    // Solicitud de branding de origen
    private Long brandingRequestId;
    private String brandName;

    // Juego
    private Long gameId;
    private String gameTitle;

    // Objetivo de campaña (definido en la solicitud de origen)
    private CampaignGoal campaignGoal;

    private Integer maxSessionsPerUserPerDay;

    // Presupuesto (en centavos)
    private Long budgetCents;
    private Long spentCents;

    // Estimación de recompensas
    private Long estimatedSessions;
    private Long costPerSessionCents;

    // Métricas
    private Long sessionsPlayed;
    private Long completedSessions;
    private Long totalPlayTimeSeconds;
    private Long uniquePlayersCount;

    // Estado
    private CampaignStatus status;

    // Fechas
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    // NECESARIOS PARA EDIT MODAL
    private List<Category> categories;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;
    private List<MunicipalityResponseDTO> targetMunicipalities;
}
