package com.verygana2.services.interfaces.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.requests.ClaimPrizeRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.models.enums.raffles.PrizeStatus;

public interface RaffleWinnerService {

    List<WinnerSummaryResponseDTO> getRaffleWinnersByRaffleId (Long raffleId);
    PagedResponse<PrizeWonResponseDTO> getWonPrizesList (Long consumerId, PrizeStatus status, Pageable pageable);
    List<WinnerSummaryResponseDTO> getLastRaffleWinners();
    void claimPrize (Long consumerId, ClaimPrizeRequestDTO request);

    /**
     * Marca como EXPIRED los premios PENDING cuyo plazo de reclamación
     * (RaffleWinner.claimDeadline) ya venció sin ser reclamados.
     * @return cantidad de premios marcados como expirados
     */
    int expireOverduePrizes();
}
