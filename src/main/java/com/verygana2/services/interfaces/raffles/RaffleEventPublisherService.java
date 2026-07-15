package com.verygana2.services.interfaces.raffles;

import java.util.List;

import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;
import com.verygana2.models.raffles.RaffleWinner;

public interface RaffleEventPublisherService {
    void publishDrawingStarted (Long raffleId, int totalWinners, long totalTickets, int maxTickets);
    void publishWinnersWithDelay (Long raffleId, List<RaffleWinner> winners, String raffleTitle);
    void publishDrawCompleted (Long raffleId, List<RaffleWinner> winners, String raffleTitle, long totalParticipants);
    void publishWaitingRoomUpdate (Long raffleId, Integer viewerCount, long secondsUntilDraw, Integer totalTickets, Integer totalParticipants, List<PrizeResponseDTO> prizes); 
}
