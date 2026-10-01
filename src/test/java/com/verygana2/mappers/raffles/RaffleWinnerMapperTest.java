package com.verygana2.mappers.raffles;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RaffleWinnerMapper")
class RaffleWinnerMapperTest {

    private final RaffleWinnerMapper raffleWinnerMapper = new RaffleWinnerMapperImpl();

    @Nested
    @DisplayName("toWinnerSummaryResponseDTO")
    class ToWinnerSummaryResponseDTO {

        @Test
        @DisplayName("caso feliz: raffleTitle sale de la cadena winner.raffleResult.raffle.title")
        void happyPath_mapsRaffleTitleThroughTwoHops() {
            Raffle raffle = new Raffle();
            raffle.setTitle("Rifa de prueba");
            RaffleResult result = new RaffleResult();
            result.setRaffle(raffle);
            Prize prize = new Prize();
            prize.setTitle("Consola");
            prize.setValue(BigDecimal.TEN);
            prize.setPosition(1);
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setUserName("juan");

            RaffleWinner winner = new RaffleWinner();
            winner.setRaffleResult(result);
            winner.setPrize(prize);
            winner.setWinner(consumer);

            WinnerSummaryResponseDTO dto = raffleWinnerMapper.toWinnerSummaryResponseDTO(winner);

            assertThat(dto.getUserName()).isEqualTo("juan");
            assertThat(dto.getRaffleTitle()).isEqualTo("Rifa de prueba");
            assertThat(dto.getPrizeTitle()).isEqualTo("Consola");
            assertThat(dto.getPrizeValue()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(dto.getPosition()).isEqualTo(1);
        }

        @Test
        @DisplayName("raffleResult == null: no lanza NPE, raffleTitle queda null")
        void nullRaffleResult_doesNotThrow() {
            RaffleWinner winner = new RaffleWinner();
            winner.setRaffleResult(null);
            winner.setPrize(new Prize());

            WinnerSummaryResponseDTO dto = raffleWinnerMapper.toWinnerSummaryResponseDTO(winner);

            assertThat(dto.getRaffleTitle()).isNull();
        }

        @Test
        @DisplayName("raffleResult.raffle == null: no lanza NPE, raffleTitle queda null")
        void nullRaffleInsideResult_doesNotThrow() {
            RaffleResult result = new RaffleResult();
            result.setRaffle(null);
            RaffleWinner winner = new RaffleWinner();
            winner.setRaffleResult(result);
            winner.setPrize(new Prize());

            WinnerSummaryResponseDTO dto = raffleWinnerMapper.toWinnerSummaryResponseDTO(winner);

            assertThat(dto.getRaffleTitle()).isNull();
        }

        @Test
        @DisplayName("prize == null: no lanza NPE, prizeTitle/prizeValue/position quedan null")
        void nullPrize_doesNotThrow() {
            RaffleWinner winner = new RaffleWinner();
            winner.setPrize(null);

            WinnerSummaryResponseDTO dto = raffleWinnerMapper.toWinnerSummaryResponseDTO(winner);

            assertThat(dto.getPrizeTitle()).isNull();
            assertThat(dto.getPrizeValue()).isNull();
            assertThat(dto.getPosition()).isNull();
        }
    }
}
