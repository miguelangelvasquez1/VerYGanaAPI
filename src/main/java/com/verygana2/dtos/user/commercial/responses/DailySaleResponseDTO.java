package com.verygana2.dtos.user.commercial.responses;

import java.time.ZonedDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Detalle transaccional de una venta individual — listado día a día del panel de ventas. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DailySaleResponseDTO {
    private Long purchaseItemId;
    private String productName;
    private ZonedDateTime deliveredAt;
    private Long subtotalCents;
    private Long commissionCents;
    private Long netToCommercialCents;
}
