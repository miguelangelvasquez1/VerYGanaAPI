package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.raffles.RaffleType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RaffleResultResponseDTO {

    private Long raffleId;
    private String raffleTitle;
    private String raffleImageUrl;
    private RaffleType raffleType;
    private ZonedDateTime drawnAt;
    private String drawProof;
    private Long totalParticipants;
    private Long totalTicketsIssued;
    private List<WinnerDetailResponseDTO> winners;

}
