package com.verygana2.models.branding;

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
import jakarta.persistence.OneToOne;
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

    @OneToOne(mappedBy = "campaign", fetch = FetchType.LAZY)
    private BrandingRequest brandingRequest;

    @OneToMany(mappedBy = "campaign", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GameSession> gameSessions;

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

    @Column(name = "unique_players_count")
    @Builder.Default
    private Long uniquePlayersCount = 0L;

    // ===== REWARDS CONFIG =====

    @Column(name = "score_reward_factor", nullable = false)
    private Double scoreRewardFactor;

    @Column(name = "completion_reward_cents", nullable = false)
    private Long completionRewardCents;

    @Column(name = "max_reward_per_session_cents", nullable = false)
    private Long maxRewardPerSessionCents;

    @Column(name = "average_reward_per_session_cents", nullable = false)
    private Long averageRewardPerSessionCents;

    @Column(name = "budget_cents", nullable = false)
    private Long budgetCents;

    @Column(name = "spent_cents", nullable = false)
    @Builder.Default
    private Long spentCents = 0L;

    @Column(name = "max_session_per_user_per_day")
    private Integer maxSessionsPerUserPerDay;

    @Column(name = "start_date")
    private ZonedDateTime startDate;

    @Column(name = "end_date")
    private ZonedDateTime endDate;

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

        if (scoreRewardFactor == null || scoreRewardFactor <= 0) {
            throw new ValidationException("scoreRewardFactor must be greater than 0");
        }
        if (maxRewardPerSessionCents == null || maxRewardPerSessionCents <= 0) {
            throw new ValidationException("maxRewardPerSessionCents must be greater than 0");
        }
        if (completionRewardCents == null || completionRewardCents < 0) {
            throw new ValidationException("completionRewardCents cannot be negative");
        }
        if (maxSessionsPerUserPerDay != null && maxSessionsPerUserPerDay <= 0) {
            throw new ValidationException("maxSessionsPerUserPerDay must be greater than 0");
        }

        if (spentCents == null) {
            this.spentCents = 0L;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    // ===== MÉTRICAS DERIVADAS =====

    public Long getEstimatedSessions() {
        if (budgetCents == null || averageRewardPerSessionCents == null || averageRewardPerSessionCents <= 0) {
            return null;
        }
        return budgetCents / averageRewardPerSessionCents;
    }
}
