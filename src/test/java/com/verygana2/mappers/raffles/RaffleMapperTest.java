package com.verygana2.mappers.raffles;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.requests.CreateRaffleRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleRuleResponseDTO;
import com.verygana2.dtos.raffle.responses.RaffleStatsResponseDTO;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.PrizeImageAsset;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleRule;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RaffleMapper")
class RaffleMapperTest {

    private final RaffleMapper raffleMapper = new RaffleMapperImpl();

    @Nested
    @DisplayName("toRaffle / normalizeDatesToUTC")
    class ToRaffle {

        @Test
        @DisplayName("fechas en otra zona horaria se normalizan a UTC preservando el instante")
        void datesInOtherZone_areNormalizedToUtc() {
            ZonedDateTime bogotaStart = ZonedDateTime.now(ZoneId.of("America/Bogota"));
            CreateRaffleRequestDTO request = new CreateRaffleRequestDTO();
            request.setStartDate(bogotaStart);
            request.setEndDate(bogotaStart.plusDays(5));
            request.setDrawDate(bogotaStart.plusDays(6));

            Raffle raffle = raffleMapper.toRaffle(request);

            assertThat(raffle.getStartDate().getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(raffle.getStartDate().toInstant()).isEqualTo(bogotaStart.toInstant());
            assertThat(raffle.getEndDate().getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(raffle.getEndDate().toInstant()).isEqualTo(bogotaStart.plusDays(5).toInstant());
            assertThat(raffle.getDrawDate().getZone()).isEqualTo(ZoneOffset.UTC);
            assertThat(raffle.getDrawDate().toInstant()).isEqualTo(bogotaStart.plusDays(6).toInstant());
        }

        @Test
        @DisplayName("fechas null no lanzan NPE en normalizeDatesToUTC")
        void nullDates_doNotThrow() {
            CreateRaffleRequestDTO request = new CreateRaffleRequestDTO();

            Raffle raffle = raffleMapper.toRaffle(request);

            assertThat(raffle.getStartDate()).isNull();
            assertThat(raffle.getEndDate()).isNull();
            assertThat(raffle.getDrawDate()).isNull();
        }
    }

    @Nested
    @DisplayName("toPrizeResponseDTO(Prize)")
    class ToPrizeResponseDTO {

        @Test
        @DisplayName("imageUrl sale directo del objectKey, sin el prefijo CDN (difiere de PrizeMapper.toPrizeResponseDTO)")
        void imageUrl_isRawObjectKey_withoutCdnPrefix() {
            Prize prize = new Prize();
            prize.setImageAsset(PrizeImageAsset.builder().objectKey("prizes/abc.png").build());

            PrizeResponseDTO dto = raffleMapper.toPrizeResponseDTO(prize);

            assertThat(dto.getImageUrl()).isEqualTo("prizes/abc.png");
        }

        @Test
        @DisplayName("sin imageAsset: imageUrl es null, sin NPE")
        void withoutImageAsset_imageUrlIsNull() {
            Prize prize = new Prize();
            prize.setImageAsset(null);

            PrizeResponseDTO dto = raffleMapper.toPrizeResponseDTO(prize);

            assertThat(dto.getImageUrl()).isNull();
        }
    }

    @Nested
    @DisplayName("toRaffleResponseDTO(RaffleRule)")
    class ToRaffleRuleResponseDTO {

        @Test
        @DisplayName("raffleId sale del id de la propia RaffleRule, NO del id de raffleRule.getRaffle() (comportamiento actual a revisar)")
        void raffleId_comesFromRaffleRuleOwnId() {
            Raffle raffle = new Raffle();
            raffle.setId(99L);
            RaffleRule rule = new RaffleRule();
            rule.setId(5L);
            rule.setRaffle(raffle);
            rule.setTicketEarningRule(null);

            RaffleRuleResponseDTO dto = raffleMapper.toRaffleResponseDTO(rule);

            assertThat(dto.getRaffleId()).isEqualTo(5L);
            assertThat(dto.getRaffleId()).isNotEqualTo(raffle.getId());
        }
    }

    @Nested
    @DisplayName("toRaffleStatsResponseDTO")
    class ToRaffleStatsResponseDTO {

        @Test
        @DisplayName("solo mapea id; el resto de campos quedan en su valor por defecto (se completan fuera del mapper)")
        void onlyIdIsMapped() {
            Raffle raffle = new Raffle();
            raffle.setId(7L);

            RaffleStatsResponseDTO dto = raffleMapper.toRaffleStatsResponseDTO(raffle);

            assertThat(dto.getId()).isEqualTo(7L);
            assertThat(dto.getTicketsBySource()).isNull();
            assertThat(dto.getMaxTicketsFromPurchases()).isNull();
            assertThat(dto.getMaxTicketsFromDailyLogin()).isNull();
            assertThat(dto.getMaxTicketsFromReferrals()).isNull();
            assertThat(dto.getTotalPrizesValue()).isNull();
        }
    }

    @Nested
    @DisplayName("getPrizeCount")
    class GetPrizeCount {

        @Test
        @DisplayName("lista de premios no vacía: devuelve su tamaño")
        void nonEmptyList_returnsSize() {
            Raffle raffle = new Raffle();
            raffle.setPrizes(List.of(new Prize(), new Prize()));

            assertThat(raffleMapper.getPrizeCount(raffle)).isEqualTo(2L);
        }

        @Test
        @DisplayName("lista de premios vacía: devuelve 0")
        void emptyList_returnsZero() {
            Raffle raffle = new Raffle();
            raffle.setPrizes(List.of());

            assertThat(raffleMapper.getPrizeCount(raffle)).isZero();
        }

        @Test
        @DisplayName("prizes == null: devuelve 0")
        void nullList_returnsZero() {
            Raffle raffle = new Raffle();
            raffle.setPrizes(null);

            assertThat(raffleMapper.getPrizeCount(raffle)).isZero();
        }
    }
}
