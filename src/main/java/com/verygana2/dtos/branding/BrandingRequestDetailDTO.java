package com.verygana2.dtos.branding;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.models.Category;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CampaignGoal;
import com.verygana2.models.enums.TargetGender;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingRequestDetailDTO {

    private Long id;
    private BrandingRequestStatus status;
    private String commercialName;

    // ===== Información de marca =====
    private String brandName;
    private String brandDescription;
    private String targetUrl;

    // ===== Juego seleccionado =====
    private Long gameId;
    private String gameName;
    private String gameFrontPageUrl;

    // ===== Presupuesto =====
    private Long budgetCents;

    // ===== Objetivo de campaña =====
    private CampaignGoal campaignGoal;

    // ===== Economía congelada =====
    private BigDecimal scoreRewardFactor;
    private Long averageRewardPerSessionCents;
    private Long estimatedSessions;

    // ===== Configuración de recompensas =====
    private Long completionRewardCents;
    private Long maxRewardPerSessionCents;
    private Integer maxSessionsPerUserPerDay;

    // ===== Fechas de campaña =====
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;

    // ===== Segmentación =====
    private List<Category> categories;
    private List<MunicipalityResponseDTO> targetMunicipalities;
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;

    // ===== Admin =====
    private String adminNotes;
    private String reviewedByAdminName;

    // ===== Diseñador =====
    private String designerNotes;
    private String assignedDesignerName;
    private String assignedDesignerCode;

    // ===== Recursos corporativos =====
    private List<CorporateResourceDTO> corporateResources;

    // ===== Flags de completitud (para guiar el formulario en el frontend) =====
    private boolean hasCompleteRewardConfig;
    private boolean hasCompleteTargeting;

    // ===== Auditoría =====
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
