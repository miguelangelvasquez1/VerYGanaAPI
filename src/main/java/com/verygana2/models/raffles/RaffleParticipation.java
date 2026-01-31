package com.verygana2.models.raffles;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raffle_participations", indexes = {
        @Index(name = "idx_raffle_participants", columnList = "raffle_id, consumer_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "unique_participation", columnNames = "raffle_id, consumer_id")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleParticipation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id")
    private Raffle raffle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consumer_id")
    private ConsumerDetails consumer;

    @Column(name = "tickets_count")
    private Long ticketsCount;

    @Column(name = "first_participation_at")
    private ZonedDateTime firstParticipationAt;

    @Column(name = "last_participation_at")
    private ZonedDateTime lastParticipationAt;

    @PrePersist
    public void onCreate() {
        if (this.ticketsCount == null) {
            this.ticketsCount = 0L;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.firstParticipationAt = now;
        this.lastParticipationAt = now;
    }

}
