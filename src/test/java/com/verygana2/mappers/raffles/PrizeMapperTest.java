package com.verygana2.mappers.raffles;

import java.math.BigDecimal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.verygana2.dtos.raffle.requests.CreatePrizeRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeResponseDTO;
import com.verygana2.models.enums.raffles.PrizeType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.PrizeImageAsset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PrizeMapper")
class PrizeMapperTest {

    private final PrizeMapper prizeMapper = new PrizeMapperImpl();

    @Nested
    @DisplayName("toPrize")
    class ToPrize {

        @Test
        @DisplayName("mapea los campos editables del request y deja los gestionados por el servicio en null")
        void mapsEditableFields() {
            CreatePrizeRequestDTO request = new CreatePrizeRequestDTO("Consola", "desc", "Sony",
                    BigDecimal.TEN, PrizeType.PHYSICAL, 1, 2, "code", "instructions");

            Prize prize = prizeMapper.toPrize(request);

            assertThat(prize.getTitle()).isEqualTo("Consola");
            assertThat(prize.getBrand()).isEqualTo("Sony");
            assertThat(prize.getValue()).isEqualByComparingTo(BigDecimal.TEN);
            assertThat(prize.getPosition()).isEqualTo(1);
            assertThat(prize.getQuantity()).isEqualTo(2);
            assertThat(prize.getId()).isNull();
            assertThat(prize.getRaffle()).isNull();
            assertThat(prize.getImageAsset()).isNull();
            assertThat(prize.getPrizeStatus()).isNull();
        }
    }

    @Nested
    @DisplayName("toPrizeResponseDTO")
    class ToPrizeResponseDTO {

        @Test
        @DisplayName("con imageAsset: imageUrl usa el prefijo CDN sobre el objectKey")
        void withImageAsset_usesCdnPrefix() {
            Prize prize = new Prize();
            prize.setImageAsset(PrizeImageAsset.builder().objectKey("prizes/abc.png").build());

            PrizeResponseDTO dto = prizeMapper.toPrizeResponseDTO(prize);

            assertThat(dto.getImageUrl()).isEqualTo("https://cdn.verygana.com/public/prizes/abc.png");
        }

        @Test
        @DisplayName("sin imageAsset: imageUrl es null (null-safe, a diferencia de RaffleResultMapper)")
        void withoutImageAsset_imageUrlIsNull() {
            Prize prize = new Prize();
            prize.setImageAsset(null);

            PrizeResponseDTO dto = prizeMapper.toPrizeResponseDTO(prize);

            assertThat(dto.getImageUrl()).isNull();
        }
    }
}
