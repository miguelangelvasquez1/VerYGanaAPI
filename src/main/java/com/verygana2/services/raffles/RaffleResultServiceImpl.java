package com.verygana2.services.raffles;

import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.mappers.raffles.RaffleResultMapper;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.repositories.raffles.RaffleResultRepository;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RaffleResultServiceImpl implements RaffleResultService {

    private final RaffleResultRepository raffleResultRepository;
    private final RaffleResultMapper raffleResultMapper;

    @Override
    public RaffleResult getByRaffleId(Long raffleId) {
        if (raffleId == null || raffleId <= 0) {
            throw new IllegalArgumentException("Raffle id must be positive");
        }

        return raffleResultRepository.findByRaffleId(raffleId).orElseThrow(() -> new ObjectNotFoundException(
                "Raffle result with raffle id: " + raffleId + " not found ", RaffleResult.class));
    }

    @Override
    public RaffleResultResponseDTO getResponseByRaffleId(Long raffleId) {
        return raffleResultMapper.toRaffleResultDTO(getByRaffleId(raffleId));
    }

    @Override
    public List<RaffleSummaryResultResponseDTO> getLastRaffleResults (){
        List<RaffleResult> results = raffleResultRepository.findLastRaffleResults();
        return results.stream().map(raffleResultMapper::toRaffleSummaryResultResponseDTO).toList();
    }

    

}
