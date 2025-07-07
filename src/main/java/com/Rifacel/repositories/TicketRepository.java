package com.Rifacel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.Rifacel.models.Ticket;

public interface TicketRepository extends JpaRepository<Ticket, String> {
    // Additional query methods can be defined here if needed
    
}
