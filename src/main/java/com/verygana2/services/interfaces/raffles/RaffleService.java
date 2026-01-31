package com.verygana2.services.interfaces.raffles;


import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.dtos.generic.EntityUpdatedResponseDTO;
import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.requests.UpdateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.ParticipantLeaderboardDTO;
import com.verygana2.dtos.raffle.responses.RaffleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;

public interface RaffleService {
    
    EntityCreatedResponseDTO createRaffle(CreateRaffleRequestDTO request);
    EntityUpdatedResponseDTO updateRaffle(Long raffleId, UpdateRaffleRequestDTO request);
    void activateRaffle(Long raffleId);
    void closeRaffle(Long raffleId);
    Raffle getRaffleById(Long raffleId);
    RaffleResponseDTO getRaffleResponseDTOById(Long raffleId);
    PagedResponse<RaffleResponseDTO> getRafflesByStatusAndType(RaffleStatus status, RaffleType type, Pageable pageable);
    RaffleStatsResponseDTO getRaffleStats(Long raffleId);
    List<ParticipantLeaderboardDTO> getRaffleLeaderBoard(Long raffleId);
}
