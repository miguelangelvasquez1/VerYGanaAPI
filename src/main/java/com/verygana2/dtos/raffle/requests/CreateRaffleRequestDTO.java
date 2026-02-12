package com.verygana2.dtos.raffle.requests;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CreateRaffleRequestDTO {
    
    @NotBlank(message = "Raffle title is required")
    @Size(max = 200, message = "Raffle title cannot exceed 200 characters")
    private String title;

    @NotBlank(message = "Raffle description is required")
    @Size(max = 2000, message = "Raffle description cannot exceed 2000 characters")
    private String description;

    @NotNull(message = "Raffle type cannot be null")
    private RaffleType raffleType;

    @NotNull(message = "Raffle start date cannot be null")
    private ZonedDateTime startDate;

    @NotNull(message = "Raffle end date cannot be null")
    private ZonedDateTime endDate;

    @NotNull(message = "Raffle draw date cannot be null")
    private ZonedDateTime drawDate;

    @PositiveOrZero(message = "Max total tickets cannot be negative")
    private Long maxTotalTickets;

    @PositiveOrZero(message = "Max tickets per user cannot be negative")
    private Long maxTicketsPerUser;

    @NotNull(message = "Requires pet? is required")
    private boolean requiresPet;

    @NotNull(message = "Draw method is required")
    private DrawMethod drawMethod;

    @NotNull(message = "Raffle prizes cannot be null")
    @Size(min = 1, message = "At least one prize is required")
    private List<CreatePrizeRequestDTO> prizes;

    @NotNull(message = "Raffle rules cannot be null")
    @Size(min = 1, message = "At least one rule is required")
    private List<CreateRaffleRuleRequestDTO> rules;

    @NotBlank(message = "Terms and conditions are required")
    @Size(max = 5000)
    private String termsAndConditions;
}
