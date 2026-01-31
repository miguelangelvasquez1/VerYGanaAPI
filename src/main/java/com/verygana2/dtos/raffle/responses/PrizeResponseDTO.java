package com.verygana2.dtos.raffle.responses;

import java.math.BigDecimal;

import com.verygana2.models.enums.raffles.PrizeType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PrizeResponseDTO {
    private Long id;
    private String title;
    private String description;
    private String brand;
    private BigDecimal value;
    private String imageUrl;
    private PrizeType prizeType;
    private Integer position;
    private Integer quantity;

}
