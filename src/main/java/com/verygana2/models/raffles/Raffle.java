package com.verygana2.models.raffles;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.TargetAudience;
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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
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

    @OneToOne(mappedBy = "raffle", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private RaffleResult raffleResult;

    @OneToMany(mappedBy = "raffle", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RaffleRule> raffleRules;

    @Column(nullable = false)
    @Size(max = 200, message = "Raffle title cannot exceed 200 characters")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @OneToOne(mappedBy = "raffle", fetch = FetchType.LAZY)
    private RaffleImageAsset imageAsset;

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
    private Integer maxTicketsPerUser;

    @Column(name = "max_total_tickets")
    private Integer maxTotalTickets;

    @Column(name = "total_tickets_issued")
    private Integer totalTicketsIssued;

    @Column(name = "total_participants")
    private Integer totalParticipants;

    @OneToMany(mappedBy = "raffle", cascade = CascadeType.ALL)
    private List<Prize> prizes;

    @Column(name = "requires_pet", nullable = false)
    private boolean requiresPet;

    @Enumerated(EnumType.STRING)
    @Column(name = "draw_method", nullable = false)
    private DrawMethod drawMethod;

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

    @ManyToOne(fetch = FetchType.LAZY, cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "target_audience_id")
    private TargetAudience targetAudience;

    @PrePersist
    public void onCreate() {

        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        this.raffleStatus = RaffleStatus.DRAFT;
        this.createdAt = now;
        this.updatedAt = now;
        this.totalTicketsIssued = 0;
        this.totalParticipants = 0;
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = ZonedDateTime.now(ZoneOffset.UTC);
    }

    public boolean hasReachedTotalLimit() {
        if (maxTotalTickets == null) {
            return false;
        }
        return totalTicketsIssued >= maxTotalTickets;
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

}
