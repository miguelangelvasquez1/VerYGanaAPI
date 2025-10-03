package com.VerYGana.models.ads2;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.VerYGana.models.enums2.AdStatus;
import com.VerYGana.models.enums2.Preference;
import com.VerYGana.models.userDetails2.AdvertiserDetails;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Entity
@Builder
@Data
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
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "start_date")
    private LocalDateTime startDate; //Could be null, meaning it starts immediately

    @Column(name = "end_date")
    private LocalDateTime endDate; //Could be null, meaning it runs indefinitely until maxLikes is reached

    @NotNull(message = "El presupuesto total es obligatorio")
    @DecimalMin(value = "1.00", message = "El presupuesto debe ser mayor a 0")
    @Column(name = "total_budget", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalBudget; //Se puede calcular como rewardPerLike * maxLikes

    @Column(name = "spent_budget", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal spentBudget = BigDecimal.ZERO; //Se puede calcular como rewardPerLike * currentLikes

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
    private String targetUrl; //When de user clicks the ad, where to redirect

    @Enumerated(EnumType.STRING)
    @Column(length = 50)
    private Preference category;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason; //Si el anuncio es rechazado, se puede guardar la razón aquí

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
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
        updatedAt = LocalDateTime.now();
    }

    // Métodos de utilidad
    public boolean canReceiveLike() {
        return status == AdStatus.ACTIVE && 
               currentLikes < maxLikes &&
               (endDate == null || endDate.isAfter(LocalDateTime.now())) &&
               hasRemainingBudget();
    }

    public boolean hasRemainingBudget() {
        return spentBudget.add(rewardPerLike).compareTo(totalBudget) <= 0;
    }

    public void incrementLike(BigDecimal rewardAmount) {
        this.currentLikes++;
        this.spentBudget = this.spentBudget.add(rewardAmount);
        
        // Auto-desactivar si se alcanza el límite
        if (this.currentLikes >= this.maxLikes || !hasRemainingBudget()) {
            this.status = AdStatus.COMPLETED;
        }
    }

    public BigDecimal getRemainingBudget() {
        return totalBudget.subtract(spentBudget);
    }

    public int getRemainingLikes() {
        return Math.max(0, maxLikes - currentLikes);
    }

    public double getCompletionPercentage() {
        return (currentLikes * 100.0) / maxLikes;
    }
}