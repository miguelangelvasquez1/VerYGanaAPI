package com.verygana2.services.interfaces;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.verygana2.models.raffles.Raffle;

public interface RaffleService {
    // List<Raffle> getByState(RaffleState state);
    Raffle getByName(String name);
    List<Raffle> getByDrawDateBefore(LocalDateTime dateTime);
    void addRafflePrize();
    void raffleTicketSale(Long userId, BigDecimal amount);
}
