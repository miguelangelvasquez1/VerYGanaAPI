package com.verygana2.services.interfaces.raffles;

import java.util.List;

import com.verygana2.models.raffles.RaffleWinner;

public interface RaffleEventPublisherService {
    void publishDrawingStarted (Long raffleId, long totalTickets, int totalWinners);
    void publishWinnersWithDelay (Long raffleId, List<RaffleWinner> winners, String raffleTitle);
    void publishDrawCompleted (Long raffleId, List<RaffleWinner> winners, String raffleTitle, int totalParticipants);
    void publishWaitingRoomUpdate (Long raffleId, int viewerCount, long secondsUntilDraw, long totalTickets); 
}
