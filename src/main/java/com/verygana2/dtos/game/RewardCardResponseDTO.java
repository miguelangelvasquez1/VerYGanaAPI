package com.verygana2.dtos.game;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RewardCardResponseDTO {
    private Long id;
    private String name;
    private String imageUrl;
    private String imageMessage;
    private String commercial;
    private Long regularPrice;
    private String keysMessage;
    private Double rating; 
    private String cartUrl;
    // private Long maxKeysAllowed;
    // private Long minCashCents;
    // private Integer stock;
    // private String categoryName;
}
