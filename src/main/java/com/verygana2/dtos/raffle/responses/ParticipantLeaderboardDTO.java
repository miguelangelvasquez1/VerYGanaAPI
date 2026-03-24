package com.verygana2.dtos.raffle.responses;

import com.verygana2.models.Avatar;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ParticipantLeaderboardDTO {
    private Long consumerId;
    private String userName;
    private String avatarUrl;
    private Long ticketsCount;
    private Double winProbability;
}