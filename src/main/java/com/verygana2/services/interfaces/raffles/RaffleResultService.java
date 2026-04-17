package com.verygana2.services.interfaces.raffles;

import java.util.List;

import com.verygana2.dtos.raffle.responses.DrawProofResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.models.raffles.RaffleResult;

public interface RaffleResultService {
    
    RaffleResult getByRaffleId(Long raffleId);
    RaffleResultResponseDTO getResultByRaffleId(Long raffleId);
    List<RaffleSummaryResultResponseDTO> getLastRaffleResults();
    DrawProofResponseDTO getDrawProof (Long raffleId);
}
