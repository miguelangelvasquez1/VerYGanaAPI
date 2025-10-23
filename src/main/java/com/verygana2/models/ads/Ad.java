package com.verygana2.models.ads;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.Category;
import com.verygana2.models.enums.AdStatus;
import com.verygana2.models.userDetails.AdvertiserDetails;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
        // Índice compuesto para búsqueda de anuncios activos disponibles
        @Index(
            name = "idx_ads_availability",
            columnList = "status, current_likes, max_likes, end_date, created_at"
        ),
        // Índice para búsqueda por fecha de inicio
        @Index(name = "idx_ads_start_date", columnList = "start_date"),
        // Índice por anunciante
        @Index(name = "idx_ads_advertiser", columnList = "advertiser_id"),
        // Índice para anuncios activos ordenados por fecha de creación
        @Index(name = "idx_ads_active_created", columnList = "status, created_at"),
        // Índice para anuncios completados
        @Index(name = "idx_ads_completed", columnList = "status, updated_at")
    }
)
@NoArgsConstructor
@AllArgsConstructor
public class Ad {
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

    @NotNull(message = "La recompensa por like es obligatoria")
    @DecimalMin(value = "0.01", message = "La recompensa debe ser mayor a 0")
    @DecimalMax(value = "100.00", message = "La recompensa no puede exceder 100")
    @Column(name = "reward_per_like", nullable = false, precision = 19, scale = 2)
    private BigDecimal rewardPerLike;

    @NotNull(message = "El máximo de likes es obligatorio")
    @Min(value = 1, message = "Debe permitir al menos 1 like")
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
    private ZonedDateTime startDate; // Could be null, meaning it starts immediately

    @Column(name = "end_date")
    private ZonedDateTime endDate; // Could be null, meaning it runs indefinitely until maxLikes is reached

    @Transient
    private BigDecimal totalBudget; // Se puede calcular como rewardPerLike * maxLikes

    @Builder.Default
    @Transient
    private BigDecimal spentBudget = BigDecimal.ZERO; // Se puede calcular como rewardPerLike * currentLikes

    @NotNull(message = "El anunciante es obligatorio")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "advertiser_id", nullable = false, foreignKey = @ForeignKey(name = "fk_ad_advertiser"))
    private AdvertiserDetails advertiser;

    @OneToMany(mappedBy = "ad", fetch = FetchType.LAZY)
    @Builder.Default
    private List<AdLike> likes = new ArrayList<>();

    @Column(length = 500)
    private String contentUrl;

    @Column(name = "target_url", length = 500)
    private String targetUrl; // When de user clicks the ad, where to redirect

    @ManyToMany
    @JoinTable(
        name = "ad_categories",
        joinColumns = @JoinColumn(name = "ad_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @NotNull(message = "Preferences are required")
    @Size(min = 1, message = "At least one category must be selected")
    private List<Category> categories;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; // Si el anuncio es rechazado, se puede guardar la razón aquí

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (currentLikes == null) {
            currentLikes = 0;
        }
        if (spentBudget == null) {
            spentBudget = BigDecimal.ZERO;
        }
        if (status == null) {
            status = AdStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // Métodos de utilidad
    public boolean canReceiveLike() {
        return status == AdStatus.ACTIVE &&
                currentLikes < maxLikes &&
                (endDate == null || endDate.isAfter(ZonedDateTime.now())) &&
                hasRemainingBudget();
    }

    public boolean hasRemainingBudget() {
        return getSpentBudget().add(rewardPerLike).compareTo(getTotalBudget()) <= 0;
    }

    public void incrementLike(BigDecimal rewardAmount) {
        this.currentLikes++;

        // Auto-desactivar si se alcanza el límite
        if (this.currentLikes >= this.maxLikes || !hasRemainingBudget()) {
            this.status = AdStatus.COMPLETED;
        }
    }

    public BigDecimal getRemainingBudget() {
        return getTotalBudget().subtract(getSpentBudget());
    }

    public int getRemainingLikes() {
        return Math.max(0, maxLikes - currentLikes);
    }

    public double getCompletionPercentage() {
        return (currentLikes * 100.0) / maxLikes;
    }

    public BigDecimal getSpentBudget() {
        if (rewardPerLike == null || currentLikes == null) {
            return BigDecimal.ZERO;
        }
        return rewardPerLike.multiply(BigDecimal.valueOf(currentLikes));
    }

    public BigDecimal getTotalBudget() {
        if (rewardPerLike == null || maxLikes == null) {
            return BigDecimal.ZERO;
        }
        return rewardPerLike.multiply(BigDecimal.valueOf(maxLikes));
    }
}