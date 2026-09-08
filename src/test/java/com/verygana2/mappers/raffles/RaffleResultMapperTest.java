package com.verygana2.mappers.raffles;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.responses.RaffleResultResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleSummaryResultResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerDetailResponseDTO;
import com.verygana2.models.enums.raffles.PrizeType;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.models.raffles.RaffleResult;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RaffleResultMapper")
class RaffleResultMapperTest {

    private final RaffleResultMapper raffleResultMapper = new RaffleResultMapperImpl();

    @Nested
    @DisplayName("toRaffleSummaryResultResponseDTO")
    class ToRaffleSummaryResultResponseDTO {

        @Test
        @DisplayName("mapea raffleId/raffleTitle/raffleType desde raffle y drawnAt desde el resultado")
        void mapsFields() {
            Raffle raffle = new Raffle();
            raffle.setId(1L);
            raffle.setTitle("Rifa");
            raffle.setRaffleType(RaffleType.STANDARD);
            ZonedDateTime drawnAt = ZonedDateTime.now(ZoneOffset.UTC);
            RaffleResult result = new RaffleResult();
            result.setRaffle(raffle);
            result.setDrawnAt(drawnAt);

            RaffleSummaryResultResponseDTO dto = raffleResultMapper.toRaffleSummaryResultResponseDTO(result);

            assertThat(dto.getRaffleId()).isEqualTo(1L);
            assertThat(dto.getRaffleTitle()).isEqualTo("Rifa");
            assertThat(dto.getRaffleType()).isEqualTo(RaffleType.STANDARD);
            assertThat(dto.getDrawnAt()).isEqualTo(drawnAt);
        }
    }

    @Nested
    @DisplayName("toRaffleResultDTO")
    class ToRaffleResultDTO {

        @Test
        @DisplayName("raffle con imageAsset: raffleImageUrl usa el prefijo CDN sobre el objectKey")
        void withImageAsset_usesCdnPrefix() {
            Raffle raffle = new Raffle();
            raffle.setId(1L);
            raffle.setImageAsset(RaffleImageAsset.builder().objectKey("raffles/abc.png").build());
            RaffleResult result = new RaffleResult();
            result.setRaffle(raffle);

            RaffleResultResponseDTO dto = raffleResultMapper.toRaffleResultDTO(result);

            assertThat(dto.getRaffleImageUrl()).isEqualTo("https://cdn.verygana.com/public/raffles/abc.png");
        }

        @Test
        @DisplayName("raffle sin imageAsset: buildImageUrl es null-safe, raffleImageUrl queda null")
        void withoutImageAsset_raffleImageUrlIsNull() {
            Raffle raffle = new Raffle();
            raffle.setId(1L);
            raffle.setImageAsset(null);
            RaffleResult result = new RaffleResult();
            result.setRaffle(raffle);

            RaffleResultResponseDTO dto = raffleResultMapper.toRaffleResultDTO(result);

            assertThat(dto.getRaffleImageUrl()).isNull();
        }

        @Test
        @DisplayName("raffleResult.raffle es null: no lanza NPE; campos derivados de raffle quedan null, raffleImageUrl incluido")
        void nullRaffle_doesNotThrow() {
            RaffleResult result = new RaffleResult();
            result.setRaffle(null);

            RaffleResultResponseDTO dto = raffleResultMapper.toRaffleResultDTO(result);

            assertThat(dto.getRaffleId()).isNull();
            assertThat(dto.getRaffleTitle()).isNull();
            assertThat(dto.getRaffleType()).isNull();
            assertThat(dto.getTotalParticipants()).isNull();
            assertThat(dto.getTotalTicketsIssued()).isNull();
            assertThat(dto.getRaffleImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("toWinnerDetailDTO")
    class ToWinnerDetailDTO {

        private RaffleWinner winnerWithPrize(Prize prize) {
            RaffleWinner winner = new RaffleWinner();
            ConsumerDetails consumer = new ConsumerDetails();
            consumer.setUserName("juan");
            winner.setWinner(consumer);
            RaffleTicket ticket = new RaffleTicket();
            ticket.setTicketNumber("000123");
            winner.setWinningTicket(ticket);
            winner.setPrize(prize);
            return winner;
        }

        @Test
        @DisplayName("prize con imageAsset: prizeImageUrl usa el prefijo CDN sobre el objectKey")
        void withImageAsset_usesCdnPrefix() {
            Prize prize = new Prize();
            prize.setTitle("Consola");
            prize.setValue(BigDecimal.TEN);
            prize.setPosition(1);
            prize.setPrizeType(PrizeType.PHYSICAL);
            prize.setImageAsset(PrizeImageAsset.builder().objectKey("prizes/xyz.png").build());

            WinnerDetailResponseDTO dto = raffleResultMapper.toWinnerDetailDTO(winnerWithPrize(prize));

            assertThat(dto.getUserName()).isEqualTo("juan");
            assertThat(dto.getTicketNumber()).isEqualTo("000123");
            assertThat(dto.getPrizeImageUrl()).isEqualTo("https://cdn.verygana.com/public/prizes/xyz.png");
        }

        @Test
        @DisplayName("prize sin imageAsset: prizeImageUrl queda null (buildImageUrl es null-safe)")
        void withoutImageAsset_prizeImageUrlIsNull() {
            Prize prize = new Prize();
            prize.setImageAsset(null);

            WinnerDetailResponseDTO dto = raffleResultMapper.toWinnerDetailDTO(winnerWithPrize(prize));

            assertThat(dto.getPrizeImageUrl()).isNull();
        }

        @Test
        @DisplayName("winner == null: no lanza NPE")
        void nullWinner_doesNotThrow() {
            WinnerDetailResponseDTO dto = raffleResultMapper.toWinnerDetailDTO(null);

            assertThat(dto).isNull();
        }
    }
}
