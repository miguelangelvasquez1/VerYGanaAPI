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
    private String image_url;
    private String image_message;
    private String commercial;
    private Long regular_price;
    private String keys_message;
    private Double rating; 
    private Long max_keys_allowed;
    private Long min_cash_cents;
    private Integer stock;
    private String category_name;
}
