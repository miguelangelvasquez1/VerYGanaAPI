package com.verygana2.models.raffles;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.userDetails.AdminDetails;

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
    private List<RaffleTicket> tickets;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AdminDetails createdBy;

    @Column(name = "terms_and_conditions", columnDefinition = "TEXT")
    private String termsAndConditions;

    // ========== LÍMITES GLOBALES ==========
    
    @Column(name = "max_total_tickets")
    private Long maxTotalTickets; // null = sin límite

    @Column(name = "max_tickets_per_user")
    private Long maxTicketsPerUser;

    // ========== LÍMITES POR FUENTE ==========
    
    @Column(name = "max_tickets_from_purchases")
    private Long maxTicketsFromPurchases;
    
    @Column(name = "max_tickets_from_ads")
    private Long maxTicketsFromAds;
    
    @Column(name = "max_tickets_from_games")
    private Long maxTicketsFromGames;
    
    @Column(name = "max_tickets_from_referrals")
    private Long maxTicketsFromReferrals;

    @Column(name = "max_tickets_platform_gifts")
    private Long maxTicketsFromPlatformGifts;
    
    // ========== CONTADORES ACTUALES ==========
    
    @Column(name = "current_tickets_from_purchases")
    private Long currentTicketsFromPurchases = 0L;
    
    @Column(name = "current_tickets_from_ads")
    private Long currentTicketsFromAds = 0L;
    
    @Column(name = "current_tickets_from_games")
    private Long currentTicketsFromGames = 0L;
    
    @Column(name = "current_tickets_from_referrals")
    private Long currentTicketsFromReferrals = 0L;

    @Column(name = "max_tickets_from_platform_gifts")
    private Long currentTicketsFromPlatformGifts;

    @PrePersist
    public void onCreate() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.raffleStatus = RaffleStatus.ACTIVE;
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

    /**
     * Verifica si se pueden emitir más tickets de una fuente específica
     */
    public boolean canIssueTicketsFromSource(RaffleTicketSource source, int quantity) {
        Long max = getMaxForSource(source);
        if (max == null) return true; // Sin límite
        
        Long current = getCurrentForSource(source);
        return (current + quantity) <= max;
    }
    
    /**
     * Verifica si se alcanzó el límite total
     */
    public boolean hasReachedTotalLimit() {
        if (maxTotalTickets == null) return false;
        return totalTicketsIssued >= maxTotalTickets;
    }
    
    /**
     * Incrementa el contador de una fuente específica
     */
    public void incrementSourceCounter(RaffleTicketSource source, int quantity) {
        switch (source) {
            case PURCHASE -> currentTicketsFromPurchases += quantity;
            case ADS_WATCHED -> currentTicketsFromAds += quantity;
            case GAME_ACHIEVEMENT -> currentTicketsFromGames += quantity;
            case REFERRAL -> currentTicketsFromReferrals += quantity;
            case PLATFORM_GIFT -> currentTicketsFromPlatformGifts += quantity;
        }
        totalTicketsIssued += quantity;
    }
    
    private Long getMaxForSource(RaffleTicketSource source) {
        return switch (source) {
            case PURCHASE -> maxTicketsFromPurchases;
            case ADS_WATCHED -> maxTicketsFromAds;
            case GAME_ACHIEVEMENT -> maxTicketsFromGames;
            case REFERRAL -> maxTicketsFromReferrals;
            case PLATFORM_GIFT -> maxTicketsFromPlatformGifts;
            default -> null;
        };
    }
    
    private Long getCurrentForSource(RaffleTicketSource source) {
        return switch (source) {
            case PURCHASE -> currentTicketsFromPurchases;
            case ADS_WATCHED -> currentTicketsFromAds;
            case GAME_ACHIEVEMENT -> currentTicketsFromGames;
            case REFERRAL -> currentTicketsFromReferrals;
            case PLATFORM_GIFT -> currentTicketsFromPlatformGifts;
            default -> 0L;
        };
    }

}
