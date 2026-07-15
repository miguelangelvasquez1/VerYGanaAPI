package com.verygana2.models.branding;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CampaignGoal;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.GameDesignerDetails;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "branding_requests",
    indexes = {
        @Index(name = "idx_branding_commercial", columnList = "commercial_id"),
        @Index(name = "idx_branding_status", columnList = "status"),
        @Index(name = "idx_branding_designer", columnList = "assigned_designer_id")
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== PARTES INVOLUCRADAS =====

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_config_definition_id", nullable = false)
    private GameConfigDefinition gameConfigDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_designer_id")
    private GameDesignerDetails assignedDesigner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private AdminDetails reviewedByAdmin;

    // ===== INFORMACIÓN DE MARCA =====

    @Column(name = "brand_name", nullable = false, length = 200)
    private String brandName;

    @Column(name = "brand_description", nullable = false, length = 1000)
    private String brandDescription;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    // ===== PRESUPUESTO (en centavos) =====

    @Column(name = "budget_cents", nullable = false)
    private Long budgetCents;

    @Column(name = "max_sessions_per_user_per_day")
    private Integer maxSessionsPerUserPerDay;

    // ===== FECHAS DE CAMPAÑA =====

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    // ===== SEGMENTACIÓN =====

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "target_audience_id")
    private TargetAudience targetAudience;

    // ===== OBJETIVO DE CAMPAÑA =====

    @Enumerated(EnumType.STRING)
    @Column(name = "campaign_goal", length = 30)
    private CampaignGoal campaignGoal;

    // ===== ESTADO Y NOTAS =====

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private BrandingRequestStatus status;

    @Column(name = "admin_notes", length = 1000)
    private String adminNotes;

    // ===== RECURSOS CORPORATIVOS =====

    @OneToMany(mappedBy = "brandingRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CorporateResource> corporateResources = new ArrayList<>();

    // ===== CONFIGURACIÓN DEL JUEGO =====

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "game_config", columnDefinition = "json")
    private Map<String, Object> gameConfig;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_form_data", columnDefinition = "json")
    private Map<String, Object> draftFormData;

    // ===== CAMPAÑA GENERADA =====

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    // ===== AUDITORÍA =====

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = BrandingRequestStatus.DRAFT;
        if (draftFormData == null) draftFormData = new HashMap<>();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // ===== TRANSICIONES DE ESTADO =====

    public boolean canBeSubmitted() {
        return status == BrandingRequestStatus.DRAFT;
    }

    public boolean canBeUpdatedByCommercial() {
        return status == BrandingRequestStatus.DRAFT
            || status == BrandingRequestStatus.APPROVED
            || status == BrandingRequestStatus.DESIGN_IN_PROGRESS
            || status == BrandingRequestStatus.CHANGES_REQUESTED
            || status == BrandingRequestStatus.PENDING_REVIEW
            || status == BrandingRequestStatus.PENDING_ADVERTISER_APPROVAL;
    }

    public boolean canBeUpdatedByDesigner() {
        return status == BrandingRequestStatus.APPROVED
            || status == BrandingRequestStatus.DESIGN_IN_PROGRESS
            || status == BrandingRequestStatus.CHANGES_REQUESTED;
    }

    public boolean canStartDesign() {
        return status == BrandingRequestStatus.APPROVED;
    }

    public boolean canSubmitDesignForReview() {
        return status == BrandingRequestStatus.DESIGN_IN_PROGRESS
            || status == BrandingRequestStatus.CHANGES_REQUESTED;
    }

    public boolean canBeReviewedByAdvertiser() {
        return status == BrandingRequestStatus.PENDING_ADVERTISER_APPROVAL;
    }

    public boolean hasCompleteTargeting() {
        if (targetAudience == null) return false;
        List<com.verygana2.models.Category> cats = targetAudience.getCategories();
        return cats != null && !cats.isEmpty()
            && targetAudience.getMinAge() != null
            && targetAudience.getMaxAge() != null
            && targetAudience.getTargetGender() != null
            && campaignGoal != null
            && maxSessionsPerUserPerDay != null
            && startDate != null;
    }

    // ===== ECONOMÍA (derivada de gameConfigDefinition) =====

    public Double getScoreRewardFactor() {
        Double factor = gameConfigDefinition.getScoreRewardFactor();
        return factor != null ? factor : null;
    }

    public Long getAverageRewardPerSessionCents() {
        return gameConfigDefinition.getAverageRewardPerSessionCents();
    }

    public Long getCompletionRewardCents() {
        return gameConfigDefinition.getCompletionRewardCents();
    }

    public Long getMaxRewardPerSessionCents() {
        return gameConfigDefinition.getMaxRewardPerSessionCents();
    }

    public Long getEstimatedSessions() {
        Long averageRewardPerSessionCents = getAverageRewardPerSessionCents();
        if (budgetCents == null || averageRewardPerSessionCents == null || averageRewardPerSessionCents <= 0) {
            return null;
        }
        return budgetCents / averageRewardPerSessionCents;
    }
}
