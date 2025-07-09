package com.Rifacel.services.interfaces;

import java.time.LocalDateTime;
import java.util.List;

import com.Rifacel.models.Raffle;
import com.Rifacel.models.Enums.RaffleState;

public interface RaffleService {
    List<Raffle> getByState(RaffleState state);
    Raffle getByName(String name);
    List<Raffle> getByEndDateBefore(LocalDateTime dateTime);
}
