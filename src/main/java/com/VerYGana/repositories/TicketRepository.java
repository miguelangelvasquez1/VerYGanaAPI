package com.VerYGana.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.VerYGana.models.Ticket;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, String> {
    List<Ticket> findByUserId (String userId);
    List<Ticket> findByRaffleId (String raffleId);
    Optional<Ticket> findByRaffleIdAndNumber(String raffleId, String number);
    boolean existsByRaffleIdAndNumber(String raffleId, String number);
}
