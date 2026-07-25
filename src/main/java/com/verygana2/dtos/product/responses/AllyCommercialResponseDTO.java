package com.verygana2.dtos.product.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AllyCommercialResponseDTO {
    private Long commercialId;
    private String companyName;
    private String planCode;
}
