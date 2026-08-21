package com.verygana2.dtos.finance.plans.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

/** Request para solicitar una recarga de saldo STANDARD/PREMIUM (contrato antes de pagar). */
@Data
public class RechargeRequestDTO {

    @NotNull(message = "El monto es requerido")
    @Positive(message = "El monto debe ser positivo")
    private Long amountCents;
}
