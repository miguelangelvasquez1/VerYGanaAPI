package com.verygana2.dtos.product.responses;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AllyCommercialResponseDTO {
    private UUID commercialPublicId;
    private String companyName;
    private String planCode;
}
