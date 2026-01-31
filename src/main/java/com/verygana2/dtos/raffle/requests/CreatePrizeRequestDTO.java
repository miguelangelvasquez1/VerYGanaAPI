package com.verygana2.dtos.raffle.requests;

import java.math.BigDecimal;

import com.verygana2.models.enums.raffles.PrizeType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreatePrizeRequestDTO {

    @NotBlank(message = "Prize title is required")
    @Size(max = 200, message = "Prize title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Prize description is required")
    @Size(max = 500, message = "Prize description cannot exceed 300 characters")
    private String description;

    @NotBlank(message = "Prize brand is required")
    private String brand;

    @NotNull(message = "Prize value cannot be null")
    @Positive(message = "Prize value must be positive")
    private BigDecimal value;

    @NotBlank(message = "Prize image is required")
    private String imageUrl;

    @NotNull(message = "Prize type cannot be null")
    private PrizeType prizeType;

    @NotNull(message = "Prize position cannot be null")
    @Positive(message = "Prize position must be positive")
    private Integer position;

    @NotNull(message = "Prize quantity cannot be null")
    @Positive(message = "Prize quantity must be positive")
    private Integer quantity;

    private boolean requiresShipping;
    private Integer estimatedDeliveryDays;
    private String redemptionInstructions;
}
