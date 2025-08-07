package com.VerYGana.models;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import lombok.Data;

@Entity
@Data
public class AdLike {

    @EmbeddedId
    private AdLikeId id;

    @ManyToOne
    @MapsId("userId")
    @JoinColumn(nullable = false)
    private User user;

    @ManyToOne
    @MapsId("adId")
    @JoinColumn(nullable = false)
    private Ad ad;

    @Column(nullable = false)
    private BigDecimal rewardAmount;
    private LocalDateTime createdAt;
    //Transaction?
}
