package com.verygana2.dtos.raffle.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantLeaderboardDTO {
    private Long consumerId;
    private String userName;
    private String profileImageUrl;
    private Long ticketsCount;
    private Double winProbability;
}