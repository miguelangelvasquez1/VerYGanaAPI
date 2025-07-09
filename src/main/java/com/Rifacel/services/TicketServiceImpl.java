package com.Rifacel.services;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.Rifacel.models.Ticket;
import com.Rifacel.repositories.TicketRepository;
import com.Rifacel.services.interfaces.TicketService;

@Service
public class TicketServiceImpl implements TicketService{

    @Autowired
    private TicketRepository ticketRepository;

    @Override
    public List<Ticket> getByUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("invalid user id");
        }
        return ticketRepository.findByUserId(userId);
    }

    @Override
    public List<Ticket> getByRaffleId(String raffleId) {
        if (raffleId == null || raffleId.isBlank()) {
            throw new IllegalArgumentException("invalid raffle id");
        }
        return ticketRepository.findByRaffleId(raffleId);
    }

    // @Override
    // public Ticket findByRaffleAndNumber(String raffleId, String number) {
    //     if (raffleId == null || raffleId.isBlank()) {
    //         throw new IllegalArgumentException("invalid raffle id");
    //     }
    //     if (number == null || number.isBlank()) {
    //         throw new IllegalArgumentException("invalid ticket number");
    //     }
    //     return ticketRepository.findByRaffleAndNumber(raffleId, number)
    //             .orElseThrow(() -> new IllegalArgumentException("Ticket not found for raffle " + raffleId + " and number " + number));
    // }

    @Override
    public boolean existsByRaffleAndNumber(String raffleId, String number) {
        if (raffleId == null || raffleId.isBlank()) {
            throw new IllegalArgumentException("invalid raffle id");
        }
        if (number == null || number.isBlank()) {
            throw new IllegalArgumentException("invalid ticket number");
        }
        return ticketRepository.existsByRaffleIdAndNumber(raffleId, number);
    }
    
}
