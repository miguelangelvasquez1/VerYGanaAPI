package com.VerYGana.services.interfaces;

import java.util.List;

import com.VerYGana.models.Ticket;

public interface TicketService {
    List<Ticket> getByUserId (String userId);
    List<Ticket> getByRaffleId (String raffleId);
    Ticket findByRaffleIdAndNumber(String raffleId, String number);
    boolean existsByRaffleAndNumber(String raffleId, String number);
}
