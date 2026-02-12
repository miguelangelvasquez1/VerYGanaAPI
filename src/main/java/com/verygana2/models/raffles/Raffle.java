package com.verygana2.models.raffles;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;

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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raffles", indexes = {
        @Index(name = "idx_status", columnList = "raffle_status"),
        @Index(name = "idx_type", columnList = "raffle_type"),
        @Index(name = "idx_dates", columnList = "start_date, end_date")
})
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Raffle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToMany(mappedBy = "raffle", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RaffleRule> raffleRules;

    @Column(nullable = false)
    @Size(max = 200, message = "Raffle title cannot exceed 200 characters")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "raffle_type", nullable = false)
    private RaffleType raffleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "raffle_status", nullable = false)
    RaffleStatus raffleStatus;

    @Column(name = "start_date", nullable = false)
    private ZonedDateTime startDate;

    @Column(name = "end_date", nullable = false)
    private ZonedDateTime endDate;

    @Column(name = "draw_date", nullable = false)
    private ZonedDateTime drawDate;

    @Column(name = "max_tickets_per_user")
    private Long maxTicketsPerUser;

    @Column(name = "max_total_tickets")
    private Long maxTotalTickets; // null = sin límite

    @Column(name = "total_tickets_issued")
    private Long totalTicketsIssued;

    @Column(name = "total_participants")
    private Long totalParticipants;

    @OneToMany(mappedBy = "raffle", cascade = CascadeType.ALL)
    private List<Prize> prizes;

    @Column(name = "requires_pet", nullable = false)
    private boolean requiresPet;

    @Enumerated(EnumType.STRING)
    @Column(name = "draw_method", nullable = false)
    private DrawMethod drawMethod;

    @Column(name = "draw_proof", columnDefinition = "TEXT")
    private String drawProof;

    @OneToMany(mappedBy = "raffle")
    private List<RaffleTicket> issuedTickets;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "modified_by")
    private Long modifiedBy;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;
    

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.raffleStatus = RaffleStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
        this.totalTicketsIssued = 0L;
        this.totalParticipants = 0L;
        this.requiresPet = false;
        validateDates();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        validateDates();
    }

    private void validateDates() {
        if (endDate != null && startDate != null && endDate.isBefore(startDate)) {
            throw new IllegalStateException("End date must be after start date");
        }
        if (drawDate != null && endDate != null && drawDate.isBefore(endDate)) {
            throw new IllegalStateException("Draw date must be after end date");
        }
    }

    /**
     * Obtiene el premio principal (posición 1)
     */
    public Prize getMainPrize() {
        return prizes.stream()
            .filter(p -> p.getPosition() == 1)
            .findFirst()
            .orElse(null);
    }

    /**
     * Obtiene el valor total de todos los premios
     */
    public BigDecimal getTotalPrizesValue() {
        return prizes.stream()
            .map(Prize::getValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean hasReachedTotalLimit() {
        if (maxTotalTickets == null) {
            return false;
        }
        return totalTicketsIssued >= maxTotalTickets;
    }

    public Long getAvailableTickets() {
        if (maxTotalTickets == null) {
            return null;
        }
        return Math.max(0, maxTotalTickets - totalTicketsIssued);
    }

    public void incrementTicketCount(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.totalTicketsIssued += quantity;
    }

    public void incrementParticipantCount() {
        this.totalParticipants++;
    }

    public boolean isActiveAndNotExpired() {
        if (raffleStatus != RaffleStatus.ACTIVE) {
            return false;
        }
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        return now.isBefore(endDate);
    }

    public List<RaffleRule> getActiveRules() {
        return raffleRules.stream()
                .filter(RaffleRule::isActive)
                .filter(config -> config.getTicketEarningRule().isActive())
                .toList();
    }

    public boolean canBeDrawn() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        return raffleStatus == RaffleStatus.CLOSED &&
               now.isAfter(drawDate) &&
               !prizes.isEmpty() &&
               totalTicketsIssued > 0;
    }

}
