package com.verygana2.dtos.raffle.requests;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.raffles.RaffleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UpdateRaffleRequestDTO {

    @NotBlank(message = "Raffle title is required")
    @Size(max = 200, message = "Raffle title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Raffle description is required")
    @Size(max = 500, message = "Raffle description cannot exceed 200 characters")
    private String description;

    @NotNull(message = "Raffle type cannot be null")
    private RaffleType raffleType;

    @NotNull(message = "requires pet? is required")
    private Boolean requiresPet;

    @NotNull(message = "Raffle start date cannot be null")
    private ZonedDateTime startDate;

    @NotNull(message = "Raffle end date cannot be null")
    private ZonedDateTime endDate;

    @NotNull(message = "Raffle draw date cannot be null")
    private ZonedDateTime drawDate;

}
