package com.verygana2.models.games;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.Category;
import com.verygana2.models.Municipality;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.enums.TargetGender;
import com.verygana2.models.userDetails.AdvertiserDetails;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "campaigns",
    indexes = {
        @Index(name = "idx_campaign_game", columnList = "game_id"),
        @Index(name = "idx_campaign_advertiser", columnList = "advertiser_id"),
        @Index(name = "idx_campaign_status", columnList = "status")
    }
)
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Campaign {

    @Version
    private Long version;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "advertiser_id", nullable = false)
    private AdvertiserDetails advertiser;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSession> gameSessions;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asset> assets;

    // Stats persistidas
    @Column(name = "sessions_played") // Total sessions on this campaign
    @Builder.Default
    private Long sessionsPlayed = 0L;

    @Column(name = "completed_sessions") // Total sessions on this campaign that were completed
    @Builder.Default
    private Long completedSessions = 0L;

    @Column(name = "total_play_time_seconds") // Total play time of all sessions on this campaign
    @Builder.Default
    private Long totalPlayTimeSeconds = 0L;

    // Rewards config --------------------

    @Column(name = "coin_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal coinValue;
    @Column(name = "completion_coins", nullable = false)
    private Integer completionCoins; //completionCoins > maxCoinsPerSession
    @Column(name = "budget_coins", nullable = false)
    private Integer budgetCoins;
    @Column(name = "spent_coins", nullable = false)
    @Builder.Default
    private Integer spentCoins = 0;

    @Column(name = "max_coins_per_session", nullable = false)
    private Integer maxCoinsPerSession;
    @Column(name = "max_session_per_user_per_day", nullable = false)
    private Integer maxSessionsPerUserPerDay;

    @Column(name = "budget", precision = 12, scale = 2, nullable = false)
    private BigDecimal budget; //Calculated: coinValue * budgetCoins

    @Column(name = "spent", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal spent = BigDecimal.ZERO; // Calculated: coinValue * spentCoins

    @Column(name = "start_date")
    private ZonedDateTime startDate; // Could be null, meaning it starts immediately

    @Column(name = "end_date")
    private ZonedDateTime endDate; // Could be null, meaning it runs indefinitely until maxLikes is reached

    @Column(name = "target_url", length = 500)
    private String targetUrl; // When de user clicks the campaign url, where to redirect

    // Users targeting
    @ManyToMany
    @JoinTable(
        name = "campaign_categories",
        joinColumns = @JoinColumn(name = "campaign_id"),
        inverseJoinColumns = @JoinColumn(name = "category_id")
    )
    @NotNull(message = "Preferences are required")
    @Size(min = 1, message = "At least one category must be selected")
    private List<Category> categories;

    @ManyToMany
    @JoinTable(
        name = "campaign_municipalities",
        joinColumns = @JoinColumn(name = "campaign_id"),
        inverseJoinColumns = @JoinColumn(name = "municipality_code")
    )
    @Builder.Default
    private List<Municipality> targetMunicipalities = new ArrayList<>();

    @Column(name = "min_age")
    @Min(value = 13, message = "La edad mínima debe ser 13")
    private Integer minAge;

    @Column(name = "max_age")
    @Max(value = 100, message = "La edad máxima debe ser 100")
    private Integer maxAge;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", length = 10)
    private TargetGender targetGender;


    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CampaignStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = ZonedDateTime.now();
        updatedAt = createdAt;

        if (coinValue == null || coinValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("coinValue must be greater than 0");
        }

        if (budgetCoins == null || budgetCoins <= 0) {
            throw new IllegalStateException("budgetCoins must be greater than 0");
        }

        if (maxCoinsPerSession == null || maxCoinsPerSession <= 0) {
            throw new IllegalStateException("maxCoinsPerSession must be greater than 0");
        }

        if (completionCoins == null || completionCoins < 0) {
            throw new IllegalStateException("completionCoins cannot be negative");
        }

        if (maxCoinsPerSession < completionCoins) {
            throw new IllegalStateException(
                "maxCoinsPerSession must be greater than or equal to completionCoins"
            );
        }

        if (maxSessionsPerUserPerDay == null || maxSessionsPerUserPerDay <= 0) {
            throw new IllegalStateException("maxSessionsPerUserPerDay must be greater than 0");
        }

        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new IllegalStateException("minAge cannot be greater than maxAge");
        }

        // Calculate derived values defensively
        this.budget = coinValue.multiply(BigDecimal.valueOf(budgetCoins));

        if (spent == null) {
            this.spent = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }
}
