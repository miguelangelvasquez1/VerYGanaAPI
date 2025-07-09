package com.Rifacel.controllers;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.Rifacel.models.Ticket;
import com.Rifacel.services.interfaces.TicketService;

@RestController
@RequestMapping("/tickets")
public class TicketController {
    
    @Autowired
    private TicketService ticketService;

    // Obtener lista de boletas por el id del usuario propietario
    @GetMapping("/{userId}")
    public ResponseEntity<List<Ticket>> getByUserId (@PathVariable String userId){
        List<Ticket> foundTickets = ticketService.getByUserId(userId);
        return ResponseEntity.ok(foundTickets);
    }

    // Obtener lista de boletas por el id de la rifa a la que pertenecen
    @GetMapping("/{raffleId}")
    public ResponseEntity<List<Ticket>> getByRaffleId (@PathVariable String raffleId){
        List<Ticket> foundTickets = ticketService.getByUserId(raffleId);
        return ResponseEntity.ok(foundTickets);
    }

    // Obtener una boleta con el id de la rifa a la que pertenece y su número de boleta
    @GetMapping("/{raffleId}/{number}")
    public ResponseEntity<Ticket> getByRaffleIdAndNumber(@PathVariable String raffleId, @PathVariable String number){
        Ticket foundTicket = ticketService.findByRaffleAndNumber(raffleId, number);
        return ResponseEntity.ok(foundTicket);
    }

    // Verificar si existe por el id de la rifa y número
    @GetMapping("/exists/raffleId/{raffleId}/number/{number}")
    public ResponseEntity<Boolean> existsByRaffleAndNumber(@PathVariable String raffleId, @PathVariable String number){
        return ResponseEntity.ok(ticketService.existsByRaffleAndNumber(raffleId, number));
    }
}