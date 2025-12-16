package com.verygana2.dtos.purchase.responses;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

@Data
public class PurchaseResponseDTO {
    private Long id;
    private String referenceId;
    private List<PurchaseItemResponseDTO> items;
    private Integer totalItems;
    private BigDecimal subtotal;
    private BigDecimal total;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
