package com.Rifacel.models;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.Rifacel.models.Enums.RaffleState;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Raffle {
    private String id;
    private String name;
    private List<String> phoneIds; //Por revisar si se cambia.
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private int numberWinner;
    private List<Ticket> ticketsSolds;
    private boolean PrizeDelivered;
    private Optional<User> winner;
    private RaffleState state;
}
