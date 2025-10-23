package com.verygana2.models.ads;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

import com.verygana2.models.User;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ad_likes",
    indexes = {
        @Index(name = "idx_ad_likes_created_at", columnList = "created_at"),
        @Index(name = "idx_ad_likes_user_id", columnList = "user_id"),
        @Index(name = "idx_ad_likes_ad_id", columnList = "ad_id")
    }
)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class AdLike {

    @EmbeddedId
    private AdLikeId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(nullable = false)
    private User user;

    @ManyToOne
    @MapsId("adId")
    @JoinColumn(nullable = false)
    private Ad ad;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal rewardAmount;
    @Column(nullable = false)
    private ZonedDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
        if (id == null) {
            id = new AdLikeId();
        }
        if (user != null) {
            id.setUserId(user.getId());
        }
        if (ad != null) {
            id.setAdId(ad.getId());
        }
    }

    // Constructor de conveniencia
    public AdLike(User user, Ad ad, BigDecimal rewardAmount) {
        this.user = user;
        this.ad = ad;
        this.rewardAmount = rewardAmount;
        this.id = new AdLikeId(user.getId(), ad.getId());
        this.createdAt = ZonedDateTime.now();
    }
}
