package com.verygana2.dtos.ad.responses;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AdLikedResponse {

    private boolean liked;
    private Long rewardAmount;
}
