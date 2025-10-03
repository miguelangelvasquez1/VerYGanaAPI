package com.verygana2.models.raffles;

import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.models.Reward;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Entity
@Data
public class Raffle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID) //UUID?
    private String id;

    private String name;
    private String description;

    @ManyToOne
    private Reward reward;

    private Double ticketPrice;
    private Integer totalTickets;
    private LocalDateTime createdAt;
    private LocalDateTime drawDate; // Validacion: Must be after createdAt
    private boolean isClosed;

    @OneToMany(mappedBy = "raffle")
    private List<RaffleTicket> tickets;
}
