package com.verygana2.models.raffles;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.userDetails.ConsumerDetails;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "raffle_tickets", indexes = {
        @Index(name = "idx_raffle_consumer", columnList = "raffle_id, ticket_owner_id"),
        @Index(name = "idx_ticket_number", columnList = "ticket_number"),
        @Index(name = "idx_consumer_tickets", columnList = "ticket_owner_id, status")
}, uniqueConstraints = @UniqueConstraint(name = "unique_ticket", columnNames = "raffle_id, ticket_number"))
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RaffleTicketStatus status;

    @Column(name = "ticket_number", unique = true, nullable = false)
    @Size(max = 50, message = "Ticket number cannot exceed 50 characters")
    private String ticketNumber;

    @Enumerated(EnumType.STRING)
    private RaffleTicketSource source;

    private Long sourceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "raffle_id")
    private Raffle raffle;

    @ManyToOne
    @JoinColumn(name = "ticketOwnerId")
    private ConsumerDetails ticketOwner;

    private Boolean isWinner;

    private ZonedDateTime usedAt;

    private ZonedDateTime issuedAt;

    @PrePersist
    public void onCreate (){
        this.status = RaffleTicketStatus.ACTIVE;
        this.issuedAt = ZonedDateTime.now(ZoneId.of("America/Bogota"));
        this.isWinner = false;
    }
}
