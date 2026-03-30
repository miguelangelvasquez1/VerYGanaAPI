package com.verygana2.models.raffles;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raffleResults")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id", nullable = false)
    private Raffle raffle;

    @OneToMany(mappedBy = "raffleResult", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RaffleWinner> winners;

    @Column(name = "drawn_at", nullable = false)
    private ZonedDateTime drawnAt;

    @Column(name = "draw_proof", columnDefinition = "TEXT")
    private String drawProof;

    @Column(name = "external_reference")
    private String externalReference;

    @PrePersist
    public void onCreate() {
        this.drawnAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

}
