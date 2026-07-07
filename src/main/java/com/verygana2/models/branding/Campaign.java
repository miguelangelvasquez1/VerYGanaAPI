package com.verygana2.models.branding;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.CampaignStatus;
import com.verygana2.models.games.Game;
import com.verygana2.models.games.GameConfigDefinition;
import com.verygana2.models.games.GameSession;
import com.verygana2.models.userDetails.CommercialDetails;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.ValidationException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "campaigns",
    indexes = {
        @Index(name = "idx_campaign_game", columnList = "game_id"),
        @Index(name = "idx_campaign_commercial", columnList = "commercial_id"),
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_definition_id", nullable = false)
    private GameConfigDefinition configDefinition;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_data", columnDefinition = "json", nullable = false)
    private Map<String, Object> configData;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commercial_id", nullable = false)
    private CommercialDetails commercial;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSession> gameSessions;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Asset> assets;

    // Stats persistidas
    @Column(name = "sessions_played")
    @Builder.Default
    private Long sessionsPlayed = 0L;

    @Column(name = "completed_sessions")
    @Builder.Default
    private Long completedSessions = 0L;

    @Column(name = "total_play_time_seconds")
    @Builder.Default
    private Long totalPlayTimeSeconds = 0L;

    // Rewards config
    @Column(name = "coin_value", precision = 12, scale = 4, nullable = false)
    private BigDecimal coinValue;

    @Column(name = "completion_coins", nullable = false)
    private Integer completionCoins;

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
    private BigDecimal budget;

    @Column(name = "spent", precision = 12, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal spent = BigDecimal.ZERO;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    // ===== SEGMENTACIÓN =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_audience_id")
    private TargetAudience targetAudience;

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
            throw new ValidationException("coinValue must be greater than 0");
        }
        if (budgetCoins == null || budgetCoins <= 0) {
            throw new ValidationException("budgetCoins must be greater than 0");
        }
        if (maxCoinsPerSession == null || maxCoinsPerSession <= 0) {
            throw new ValidationException("maxCoinsPerSession must be greater than 0");
        }
        if (completionCoins == null || completionCoins < 0) {
            throw new ValidationException("completionCoins cannot be negative");
        }
        if (maxCoinsPerSession < completionCoins) {
            throw new ValidationException("maxCoinsPerSession must be greater than or equal to completionCoins");
        }
        if (maxSessionsPerUserPerDay == null || maxSessionsPerUserPerDay <= 0) {
            throw new ValidationException("maxSessionsPerUserPerDay must be greater than 0");
        }

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
