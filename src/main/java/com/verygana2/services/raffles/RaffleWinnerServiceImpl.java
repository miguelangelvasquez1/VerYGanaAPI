
package com.verygana2.services.raffles;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.mappers.raffles.RaffleWinnerMapper;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RaffleWinnerServiceImpl implements RaffleWinnerService {

    private final RaffleWinnerRepository raffleWinnerRepository;
    private final RaffleWinnerMapper raffleWinnerMapper;

    @Transactional(readOnly = true)
    @Override
    public List<WinnerSummaryResponseDTO> getRaffleWinnersList(Long raffleId) {

        List<RaffleWinner> winners = raffleWinnerRepository.findByRaffleId(raffleId);

        return winners.stream().map(raffleWinnerMapper::toWinnerSummaryResponseDTO).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public List<PrizeWonResponseDTO> getWonPrizesList(Long consumerId, Pageable pageable) {

        List<RaffleWinner> wins = raffleWinnerRepository.findByWinnerId(consumerId, pageable);
        return wins.stream().map(w -> {
            Prize prize = w.getPrize();
            return PrizeWonResponseDTO.builder()
                    .prizeId(prize.getId())
                    .winnerId(w.getWinner().getId())
                    .title(prize.getTitle())
                    .description(prize.getDescription())
                    .brand(prize.getBrand())
                    .value(prize.getValue())
                    .imageUrl(prize.getImageUrl())
                    .prizeType(prize.getPrizeType())
                    .position(prize.getPosition())
                    .quantity(prize.getQuantity())
                    .ticketWinnerNumber(w.getWinningTicket().getTicketNumber())
                    .drawnAt(w.getDrawnAt())
                    .isClaimed(w.isPrizeClaimed())
                    .claimedAt(w.getPrizeClaimedAt())
                    .build();
        }).toList();
    }

    @Override
    public void claimPrize() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'claimPrize'");
    }

}
