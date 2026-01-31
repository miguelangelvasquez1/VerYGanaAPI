package com.verygana2.models.raffles;


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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raffle_winners", indexes = {
    @Index(name = "idx_raffle_winners", columnList = "raffle_id"),
    @Index(name = "idx_consumer_wins", columnList = "winner_consumer_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RaffleWinner {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prize_id", nullable = false)
    private Prize prize;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_consumer_id", nullable = false)
    private ConsumerDetails winner;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winning_ticket_id", nullable = false)
    private RaffleTicket winningTicket;
    
    @Column(name = "prize_claimed")
    private boolean prizeClaimed;

    @Column(name = "prize_claimed_at")
    private ZonedDateTime prizeClaimedAt;

    @Column(name = "prize_tracking_number")
    private String prizeTrackingNumber;

    @Column(name = "drawn_at", nullable = false)
    private ZonedDateTime drawnAt;

    @PrePersist
    public void onCreate(){
        this.prizeClaimed = false;
    }
}

