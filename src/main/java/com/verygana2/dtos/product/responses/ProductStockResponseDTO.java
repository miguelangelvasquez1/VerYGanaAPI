package com.verygana2.dtos.product.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.marketplace.StockStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductStockResponseDTO {
    private Long id;
    private StockStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime soldAt;
}
