package com.verygana2.services.interfaces.raffles;

import java.util.List;

import com.verygana2.dtos.raffle.responses.DrawResultResponseDTO;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;

public interface DrawingService {

    DrawResultResponseDTO conductDraw (Long raffleId);

    List<RaffleTicket> randomInternalDraw(List<RaffleTicket> tickets, Integer numberOfWinners);
    List<RaffleTicket> randomExternalDraw(List<RaffleTicket> tickets, Integer numberOfWinners);

    String generateDrawProof(Long raffleId, List<RaffleWinner> winners);
    boolean verifyDrawIntegrity(Long raffleId);

    void notifyWinners(Long raffleId);
    void publishResults(Long raffleId);
}
