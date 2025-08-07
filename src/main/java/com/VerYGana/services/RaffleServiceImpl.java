package com.VerYGana.services;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.VerYGana.models.Raffle;
import com.VerYGana.repositories.RaffleRepository;
import com.VerYGana.services.interfaces.RaffleService;

@Service
public class RaffleServiceImpl implements RaffleService{

    @Autowired
    private RaffleRepository raffleRepository;

    // @Override
    // public List<Raffle> getByState(RaffleState state) {
    //     if (state == null || !(state == RaffleState.AVAILABLE || state == RaffleState.PENDING || state == RaffleState.FINISHED)) {
    //         throw new IllegalArgumentException("invalid raffle state");
    //     }
    //     return raffleRepository.findByState(state);
    // }

    @Override
    public Raffle getByName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("invalid raffle name");
        }
        return raffleRepository.findByName(name).orElseThrow(() -> new ObjectNotFoundException("Raffle", Raffle.class));
    }
    
    
    @Override
    public List<Raffle> getByDrawDateBefore(LocalDateTime dateTime) {
        return raffleRepository.findByDrawDateBefore(dateTime);
    }
}
