package com.verygana2.dtos.raffle.responses;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.dtos.targeting.TargetAudienceResponseDTO;
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
    private String imageUrl;
    private String title;
    private String description;
    private RaffleType raffleType;
    private RaffleStatus raffleStatus;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private ZonedDateTime drawDate;
    private Integer maxTicketsPerUser;
    private Integer maxTotalTickets;
    private Integer totalTicketsIssued;
    private Integer totalParticipants;
    private List<PrizeResponseDTO> prizes;
    private List<RaffleRuleResponseDTO> rules;
    private boolean requiresPet;
    private DrawMethod drawMethod;
    private String termsAndConditions;
    private TargetAudienceResponseDTO targeting;

}
