package com.verygana2.models.ads;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.UUID;

import com.verygana2.models.User;
import com.verygana2.models.enums.AdWatchSessionStatus;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(
    name = "ad_watch_session",
    indexes = {
        @Index(
            name = "idx_watch_session_user_ad_expires",
            columnList = "user_id, ad_id, expires_at"
        ),
        @Index(
            name = "idx_watch_session_ad",
            columnList = "ad_id"
        )
    }
)
@AllArgsConstructor
@NoArgsConstructor
public class AdWatchSession {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne
    private User user;

    @ManyToOne
    private Ad ad;

    private ZonedDateTime startedAt;
    private ZonedDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    private AdWatchSessionStatus status;

    public AdWatchSession(User user, Ad ad) {
        this.user = user;
        this.ad = ad;
    }

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = ZonedDateTime.now(ZoneOffset.UTC);
        }
        if (expiresAt == null) {

            double duration = ad.getAsset().getDurationSeconds() != null ? ad.getAsset().getDurationSeconds() : 0;
            long durationSeconds = Math.round(duration);
            long marginSeconds = Math.max(300L, Math.round(duration * 0.1)); // 5 min de margen

            expiresAt = startedAt.plusSeconds(durationSeconds + marginSeconds);
        }
        if (status == null) {
            status = AdWatchSessionStatus.ACTIVE;
        }
    }
}
