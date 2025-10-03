package com.verygana2.models.raffles;

import java.time.LocalDateTime;

import com.verygana2.models.User;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class RaffleTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer ticketNumber; //Lógica para que sea único por rifa
    private LocalDateTime purchasedAt;

    @ManyToOne
    private Raffle raffle;

    @ManyToOne
    private User user;
}
