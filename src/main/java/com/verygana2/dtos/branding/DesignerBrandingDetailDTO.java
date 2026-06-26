package com.verygana2.dtos.branding;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import com.verygana2.dtos.MunicipalityResponseDTO;
import com.verygana2.dtos.game.campaign.GameSchemaResponse;
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
public class DesignerBrandingDetailDTO {

    private Long id;
    private BrandingRequestStatus status;

    // ===== Contexto de la marca =====
    private String commercialName;
    private String brandName;
    private String brandDescription;
    private String targetUrl;
    private CampaignGoal campaignGoal;

    // ===== Juego a brandear =====
    private Long gameId;
    private String gameName;
    private String gameFrontPageUrl;

    // ===== Recursos corporativos del anunciante (imágenes de referencia) =====
    private List<CorporateResourceDTO> corporateResources;

    // ===== Recompensas (referencia para configurar el juego) =====
    private Long completionRewardCents;
    private Long maxRewardPerSessionCents;
    private Integer maxSessionsPerUserPerDay;

    // ===== Segmentación (referencia) =====
    private Integer minAge;
    private Integer maxAge;
    private TargetGender targetGender;
    private List<Category> categories;
    private List<MunicipalityResponseDTO> targetMunicipalities;

    // ===== Comunicación con el admin =====
    private String adminNotes;

    // ===== Notas del diseñador =====
    private String designerNotes;

    // ===== Configuración del juego (schema + config guardada) =====
    private GameSchemaResponse gameSchema;
    private Map<String, Object> gameConfig;

    // Borrador del formData RJSF con los valores que el diseñador ha ido guardando
    private Map<String, Object> draftFormData;

    // ===== Auditoría =====
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
