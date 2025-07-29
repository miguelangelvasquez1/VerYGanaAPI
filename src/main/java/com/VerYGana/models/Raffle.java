package com.VerYGana.models;

import java.time.LocalDateTime;
import java.util.List;

import com.VerYGana.models.Enums.RaffleState;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Raffle {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String name;
    @OneToMany
    private List<Phone> phones;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int numberWinner;
    @OneToMany(mappedBy = "raffle", cascade = CascadeType.ALL)
    private List<Ticket> ticketsSolds;
    private boolean prizeDelivered;
    @ManyToOne
    private User winner;
    @Enumerated(EnumType.STRING)
    private RaffleState state;
}
