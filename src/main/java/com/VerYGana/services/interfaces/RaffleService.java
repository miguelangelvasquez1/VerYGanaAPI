package com.VerYGana.services.interfaces;

import java.time.LocalDateTime;
import java.util.List;

import com.VerYGana.models.raffles.Raffle;

public interface RaffleService {
    // List<Raffle> getByState(RaffleState state);
    Raffle getByName(String name);
    List<Raffle> getByDrawDateBefore(LocalDateTime dateTime);
}
