package com.verygana2.dtos.finance.plans.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangeRequestResponseDTO {
    private Long id;
    private PlanCode fromPlanCode;
    private PlanCode toPlanCode;
    private Long requiredTopUpAmountCents;
    private PlanChangeRequestStatus status;
    private Long contractId;
    /** Estado del contrato vinculado (null si aún no se generó) — evita una segunda llamada para hacer polling de la firma. */
    private ContractStatus contractStatus;
    private ZonedDateTime requestedAt;
    private ZonedDateTime appliedAt;
}
