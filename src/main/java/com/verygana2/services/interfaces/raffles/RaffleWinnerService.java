package com.verygana2.services.interfaces.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;

public interface RaffleWinnerService {

    List<WinnerSummaryResponseDTO> getRaffleWinnersList (Long raffleId);
    List<PrizeWonResponseDTO> getWonPrizesList (Long consumerId, Pageable pageable);
    void claimPrize ();
}
