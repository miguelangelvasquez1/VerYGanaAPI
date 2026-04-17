
package com.verygana2.services.raffles;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.mappers.raffles.RaffleWinnerMapper;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.RaffleResultService;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RaffleWinnerServiceImpl implements RaffleWinnerService {

    private final RaffleWinnerRepository raffleWinnerRepository;
    private final RaffleResultService raffleResultService;
    private final RaffleWinnerMapper raffleWinnerMapper;
    private static final String domain = "https://cdn.verygana.com/public/";

    @Transactional(readOnly = true)
    @Override
    public List<WinnerSummaryResponseDTO> getRaffleWinnersByRaffleId(Long raffleId) {

        RaffleResult result = raffleResultService.getByRaffleId(raffleId);
        
        List<RaffleWinner> winners = raffleWinnerRepository.findByRaffleResultId(result.getId());

        return winners.stream().map(raffleWinnerMapper::toWinnerSummaryResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public PagedResponse<PrizeWonResponseDTO> getWonPrizesList(Long consumerId, Boolean isClaimed, Pageable pageable) {

        Page<RaffleWinner> wins = raffleWinnerRepository.findWonPrizesByConsumer(consumerId, isClaimed, pageable);
        return PagedResponse.from(wins.map(w -> {
            Prize prize = w.getPrize();
            return PrizeWonResponseDTO.builder()
                    .prizeId(prize.getId())
                    .winnerId(w.getWinner().getId())
                    .title(prize.getTitle())
                    .description(prize.getDescription())
                    .brand(prize.getBrand())
                    .value(prize.getValue())
                    .imageUrl(domain + prize.getImageAsset().getObjectKey())
                    .prizeType(prize.getPrizeType())
                    .position(prize.getPosition())
                    .quantity(prize.getQuantity())
                    .ticketWinnerNumber(w.getWinningTicket().getTicketNumber())
                    .drawnAt(w.getRaffleResult().getDrawnAt())
                    .isClaimed(w.isPrizeClaimed())
                    .claimedAt(w.getPrizeClaimedAt())
                    .build();
        }));
    }

    @Override
    public List<WinnerSummaryResponseDTO> getLastRaffleWinners(){
        List<RaffleWinner> winners = raffleWinnerRepository.findLastWinners();
        return winners.stream().map(raffleWinnerMapper::toWinnerSummaryResponseDTO).toList();
    }

    @Override
    public void claimPrize() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'claimPrize'");
    }

}
