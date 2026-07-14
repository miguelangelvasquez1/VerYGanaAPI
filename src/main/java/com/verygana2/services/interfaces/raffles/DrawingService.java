package com.verygana2.services.interfaces.raffles;

import java.util.List;

import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RandomOrgDrawMetadata;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;

public interface DrawingService {

    DrawResultResponseDTO conductDraw(Long raffleId);

    List<RaffleTicket> randomInternalDraw(List<RaffleTicket> tickets, Integer numberOfWinners);

    

    /**
     * @param actualMethod      Método realmente usado (puede diferir del configurado si hubo fallback).
     * @param drawMethodNote    Nota explicativa cuando hubo fallback; null si no aplica.
     * @param randomOrgMetadata Evidencia de Random.org (serialNumber, signature, etc.);
     *                          null cuando el método real fue SYSTEM_RANDOM.
     * @param ticketPoolHash    SHA-256 del pool de tickets activos computado ANTES del sorteo.
     */
    String generateDrawProof(Long raffleId, List<RaffleWinner> winners,
                             DrawMethod actualMethod, String drawMethodNote,
                             RandomOrgDrawMetadata randomOrgMetadata,
                             String ticketPoolHash);

    boolean verifyDrawIntegrity(Long raffleId);

    void notifyWinners(Long raffleId);
}
