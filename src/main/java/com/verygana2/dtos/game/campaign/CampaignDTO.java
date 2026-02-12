package com.verygana2.dtos.game.campaign;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.models.Category;
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

    // Juego
    private Long gameId;
    private String gameTitle;

    private BigDecimal coinValue;
    private Integer completionCoins;
    private Integer budgetCoins;
    private Integer spentCoins;
    private Integer maxCoinsPerSession;
    private Integer maxSessionsPerUserPerDay;

    // Presupuesto
    private BigDecimal budget;
    private BigDecimal spent;

    // Métricas
    private Long sessionsPlayed;
    private Long completedSessions;
    private Long totalPlayTimeSeconds;

    // Estado
    private CampaignStatus status;

    // Fechas
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    // NECESARIOS PARA EDIT MODAL
    private String targetUrl;
    private List<Category> categories;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;
    private List<MunicipalityResponseDTO> targetMunicipalities;
}
