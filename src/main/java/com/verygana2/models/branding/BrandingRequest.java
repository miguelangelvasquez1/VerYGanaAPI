package com.verygana2.models.branding;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.BrandingRequestStatus;
import com.verygana2.models.enums.CampaignGoal;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.games.Game;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_designer_id")
    private GameDesignerDetails assignedDesigner;

    // Admin que revisó (aprobó o rechazó) la solicitud
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

    // ===== ECONOMÍA CONGELADA (capturada del juego al crear la solicitud) =====

    @Column(name = "score_reward_factor", precision = 10, scale = 4)
    private BigDecimal scoreRewardFactor;

    @Column(name = "average_reward_per_session_cents")
    private Long averageRewardPerSessionCents;

    @Column(name = "estimated_sessions")
    private Long estimatedSessions;

    // Cuánto gana el jugador al completar una sesión (en centavos)
    @Column(name = "completion_reward_cents")
    private Long completionRewardCents;

    // Máximo que puede ganar un jugador por sesión (en centavos)
    @Column(name = "max_reward_per_session_cents")
    private Long maxRewardPerSessionCents;

    @Column(name = "max_sessions_per_user_per_day")
    private Integer maxSessionsPerUserPerDay;

    // ===== FECHAS DE CAMPAÑA =====

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    // ===== SEGMENTACIÓN (se completan vía PATCH /config) =====

    @ManyToMany
    @JoinTable(
        name = "branding_request_categories",
        joinColumns = @JoinColumn(name = "branding_request_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @Builder.Default
    private List<Category> categories = new ArrayList<>();

    @ManyToMany
    @JoinTable(
        name = "branding_request_municipalities",
        joinColumns = @JoinColumn(name = "branding_request_id"),
        inverseJoinColumns = @JoinColumn(name = "municipality_code")
    )
    @Builder.Default
    private List<Municipality> targetMunicipalities = new ArrayList<>();

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", length = 10)
    private TargetGender targetGender;

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

    @Column(name = "designer_notes", length = 1000)
    private String designerNotes;

    // ===== RECURSOS CORPORATIVOS =====

    @OneToMany(mappedBy = "brandingRequest", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CorporateResource> corporateResources = new ArrayList<>();

    // ===== CONFIGURACIÓN DEL JUEGO (rellenada por el diseñador) =====

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "game_config", columnDefinition = "json")
    private Map<String, Object> gameConfig;

    // Borrador del formData RJSF: se actualiza al subir assets y al guardar cambios manualmente.
    // Cada clave corresponde a un campo del jsonSchema; su valor es el dato ingresado por el diseñador.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "draft_form_data", columnDefinition = "json")
    private Map<String, Object> draftFormData;

    // ===== CAMPAÑA GENERADA =====

    // Se enlaza solo cuando el admin lanza la campaña (estado LAUNCHED)
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
            || status == BrandingRequestStatus.CHANGES_REQUESTED;
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

    public boolean hasCompleteRewardConfig() {
        return completionRewardCents != null
            && maxRewardPerSessionCents != null
            && maxSessionsPerUserPerDay != null
            && maxRewardPerSessionCents >= completionRewardCents;
    }

    public boolean hasCompleteTargeting() {
        return categories != null && !categories.isEmpty();
    }
}
