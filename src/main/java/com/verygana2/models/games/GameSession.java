package com.verygana2.models.games;

import org.hibernate.annotations.ColumnDefault;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.branding.Campaign;
import com.verygana2.models.enums.DevicePlatform;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "game_sessions")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GameSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_token", unique = true, length = 100)
    private String sessionToken;

    @Column(name = "user_hash", nullable = false)
    private String userHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id", nullable = false)
    private ConsumerDetails consumer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name = "start_time", nullable = false)
    @Builder.Default
    private ZonedDateTime startTime = ZonedDateTime.now();

    @Column(name = "end_time", nullable = true)
    private ZonedDateTime endTime;

    /** Lo que se le cobró a la campaña por esta sesión (lo financiado), en centavos. */
    @Column(name = "coins_earned")
    private Long coinsEarned;

    /**
     * Lo que se le acreditó al jugador: {@link #coinsEarned} × multiplicador de nivel. La
     * diferencia con lo financiado la liquida KeyIssuanceSettlementService por lotes, igual
     * que en AdLike. Null en las sesiones anteriores a la columna y en las que no cobraron.
     */
    @Column(name = "credited_amount")
    private Long creditedAmountCents;

    /** Si el diferencial (financiado − acreditado) ya se liquidó en tesorería. */
    // Mismo DEFAULT que la migración: sin él, el esquema que genera Hibernate en los
    // tests (H2) rechaza los inserts que no mandan la columna y MySQL los acepta.
    @Column(name = "issuance_settled", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private boolean issuanceSettled = false;

    @Column(name = "play_time_seconds", nullable = true)
    private Long playTimeSeconds;

    @Column(name = "device_platform", nullable = false)
    @Enumerated(EnumType.STRING)
    private DevicePlatform devicePlatform;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "reward_granted", nullable = false)
    private boolean rewardGranted;

    @Column(name = "score", nullable = true)
    private Integer score;

    @OneToMany(mappedBy = "session")
    private List<GameSessionMetric> metrics;

    // Factory method to create a new GameSession
    public static GameSession start(
            ConsumerDetails consumer,
            Game game,
            DevicePlatform platform,
            Campaign campaign
    ) {
        GameSession session = new GameSession();
        session.sessionToken = java.util.UUID.randomUUID().toString();
        session.userHash = consumer.getUserHash();
        session.consumer = consumer;
        session.game = game;
        session.campaign = campaign;
        session.startTime = ZonedDateTime.now();
        session.completed = false;
        session.rewardGranted = false;
        session.score = null;
        session.playTimeSeconds = null;
        session.devicePlatform = platform;
        session.metrics = new ArrayList<>();
        session.endTime = null;
        return session;
    }
}
