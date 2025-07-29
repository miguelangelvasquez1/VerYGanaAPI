package com.VerYGana.services.interfaces;

import java.time.LocalDateTime;
import java.util.List;

import com.VerYGana.models.Raffle;
import com.VerYGana.models.Enums.RaffleState;

public interface RaffleService {
    List<Raffle> getByState(RaffleState state);
    Raffle getByName(String name);
    List<Raffle> getByEndDateBefore(LocalDateTime dateTime);
}
