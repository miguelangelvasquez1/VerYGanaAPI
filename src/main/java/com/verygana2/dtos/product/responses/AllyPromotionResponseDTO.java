package com.verygana2.dtos.product.responses;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AllyPromotionResponseDTO {
    private Long productId;
    private String productName;
    private String productImageUrl;
    private Long allyCommercialId;
    private String allyCommercialName;
    private Long priceCents;
    private ZonedDateTime promotedAt;
}
