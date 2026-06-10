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
     * Ejecuta el sorteo con Random.org. Lanza RandomOrgException si el servicio
     * no está disponible — no hace fallback silencioso.
     */
    List<RaffleTicket> randomExternalDraw(List<RaffleTicket> tickets, Integer numberOfWinners);

    /**
     * @param actualMethod      Método realmente usado (puede diferir del configurado si hubo fallback).
     * @param drawMethodNote    Nota explicativa cuando hubo fallback; null si no aplica.
     * @param randomOrgMetadata Evidencia de Random.org (serialNumber, completionTime, bits);
     *                          null cuando el método real fue SYSTEM_RANDOM.
     */
    String generateDrawProof(Long raffleId, List<RaffleWinner> winners,
                             DrawMethod actualMethod, String drawMethodNote,
                             RandomOrgDrawMetadata randomOrgMetadata);

    boolean verifyDrawIntegrity(Long raffleId);

    void notifyWinners(Long raffleId);
    void publishResults(Long raffleId);
}
