package com.verygana2.dtos.raffle.responses;

import java.util.HashMap;
import java.util.Map;

import lombok.Data;

/**
 * Resultado del procesamiento de obtención de tickets
 */
@Data
public class TicketEarningResult {
    
    private int totalTicketsIssued = 0;
    private int rafflesProcessed = 0;
    private int rafflesSuccessful = 0;
    private int rafflesFailed = 0;
    
    // raffleId -> cantidad de tickets emitidos
    private Map<Long, Integer> ticketsByRaffle = new HashMap<>();
    
    // raffleId -> nombre de la rifa
    private Map<Long, String> raffleNames = new HashMap<>();
    
    // raffleId -> mensaje de error
    private Map<Long, String> errors = new HashMap<>();

    public static TicketEarningResult empty() {
        return new TicketEarningResult();
    }

    public void addSuccess(Long raffleId, String raffleName, int ticketsIssued) {
        this.totalTicketsIssued += ticketsIssued;
        this.rafflesProcessed++;
        this.rafflesSuccessful++;
        this.ticketsByRaffle.put(raffleId, ticketsIssued);
        this.raffleNames.put(raffleId, raffleName);
    }

    public void addError(Long raffleId, String raffleName, String errorMessage) {
        this.rafflesProcessed++;
        this.rafflesFailed++;
        this.errors.put(raffleId, errorMessage);
        this.raffleNames.put(raffleId, raffleName);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isSuccess() {
        return rafflesSuccessful > 0;
    }
}