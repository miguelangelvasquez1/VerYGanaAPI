package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RaffleResponseDTO {

    private Long id;
    private String title;
    private String description;
    private RaffleType raffleType;
    private RaffleStatus raffleStatus;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime drawDate;
    private Long totalTicketsIssued;
    private Long totalParticipants;
    private List<PrizeResponseDTO> prizes;
    private boolean requiresPet;
    private DrawMethod drawMethod;
    private String drawProof;
    private String termsAndConditions;

}
