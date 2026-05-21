package com.verygana2.models.ads;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Builder
@Data
@Table(
    name = "ads",
    indexes = {
        @Index(name = "idx_ads_availability",  columnList = "status, current_likes, max_likes, end_date, created_at"),
        @Index(name = "idx_ads_start_date",    columnList = "start_date"),
        @Index(name = "idx_ads_commercial",    columnList = "commercial_id"),
        @Index(name = "idx_ads_active_created",columnList = "status, created_at"),
        @Index(name = "idx_ads_completed",     columnList = "status, updated_at")
    }
)
@NoArgsConstructor
@AllArgsConstructor
public class Ad {

    @Version
    @Column(nullable = false)
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El título es obligatorio")
    @Size(min = 5, max = 100, message = "El título debe tener entre 5 y 100 caracteres")
    @Column(nullable = false, length = 100)
    private String title;

    @NotBlank(message = "La descripción es obligatoria")
    @Size(min = 10, max = 1000, message = "La descripción debe tener entre 10 y 1000 caracteres")
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * Recompensa pagada al consumer por cada like, en centavos de COP.
     * Mín: 1 centavo. Máx: 10.000 centavos (100 COP).
     */
    @NotNull(message = "La recompensa por like es obligatoria")
    @Min(value = 1,     message = "La recompensa mínima es 1 centavo")
    @Max(value = 100000, message = "La recompensa no puede exceder 100.000 centavos (1.000 COP)")
    @Column(name = "reward_per_like", nullable = false)
    private Long rewardPerLike; // poner cents

    @NotNull(message = "El máximo de likes es obligatorio")
    @Min(value = 1,     message = "Debe permitir al menos 1 like")
    @Max(value = 10000, message = "No puede exceder 10,000 likes")
    @Column(name = "max_likes", nullable = false)
    private Integer maxLikes;

    @Column(name = "current_likes", nullable = false)
    @Builder.Default
    private Integer currentLikes = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AdStatus status = AdStatus.PENDING;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @NotNull(message = "El anunciante es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ad_commercial"))
    private CommercialDetails commercial;

    @OneToMany(mappedBy = "ad", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AdLike> likes = new ArrayList<>();

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @ManyToMany
    @JoinTable(
        name = "ad_categories",
        joinColumns = @JoinColumn(name = "ad_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @NotNull(message = "Preferences are required")
    @Size(min = 1, message = "At least one category must be selected")
    private List<Category> categories;

    @ManyToMany
    @JoinTable(
        name = "ad_municipalities",
        joinColumns = @JoinColumn(name = "ad_id"),
        inverseJoinColumns = @JoinColumn(name = "municipality_code")
    )
    @Builder.Default
    private List<Municipality> targetMunicipalities = new ArrayList<>();

    @OneToOne(mappedBy = "ad", cascade = CascadeType.ALL)
    private AdAsset asset;

    @Column(name = "min_age")
    @Min(value = 13, message = "La edad mínima debe ser 13")
    private Integer minAge;

    @Column(name = "max_age")
    @Max(value = 100, message = "La edad máxima debe ser 100")
    private Integer maxAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", length = 10)
    private TargetGender targetGender;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        if (createdAt == null)    createdAt    = ZonedDateTime.now();
        if (currentLikes == null) currentLikes = 0;
        if (status == null)       status       = AdStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // ── Métodos de negocio ────────────────────────────────────────────────────

    public boolean canReceiveLike() {
        return status == AdStatus.ACTIVE
                && currentLikes < maxLikes
                && (endDate == null || endDate.isAfter(ZonedDateTime.now()))
                && hasRemainingBudget();
    }

    /** Hay presupuesto para pagar al menos un like más. */
    public boolean hasRemainingBudget() {
        return (getSpentBudget() + rewardPerLike) <= getTotalBudget();
    }

    public void incrementLike() {
        this.currentLikes++;
        if (this.currentLikes >= this.maxLikes || !hasRemainingBudget()) {
            this.status = AdStatus.COMPLETED;
        }
    }

    /** Centavos ya pagados (rewardPerLike × likes actuales). */
    public Long getSpentBudget() {
        if (rewardPerLike == null || currentLikes == null) return 0L;
        return rewardPerLike * currentLikes.longValue();
    }

    /** Presupuesto total del anuncio en centavos (rewardPerLike × maxLikes). */
    public Long getTotalBudget() {
        if (rewardPerLike == null || maxLikes == null) return 0L;
        return rewardPerLike * maxLikes.longValue();
    }

    /** Centavos restantes del presupuesto. */
    public Long getRemainingBudget() {
        return getTotalBudget() - getSpentBudget();
    }

    public int getRemainingLikes() {
        return Math.max(0, maxLikes - currentLikes);
    }

    public double getCompletionPercentage() {
        return (currentLikes * 100.0) / maxLikes;
    }

    public List<Municipality> getTargetMunicipalities() {
        if (targetMunicipalities == null) targetMunicipalities = new ArrayList<>();
        return targetMunicipalities;
    }
}
