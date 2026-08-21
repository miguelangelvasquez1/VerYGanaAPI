package com.verygana2.dtos.finance.plans.responses;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan.PlanCode;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del listado de solicitudes de cambio de plan para el panel de compliance. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangeReviewListItemDTO {
    private Long id;
    private Long commercialId;
    private String companyName;
    private String email;
    private PlanCode fromPlanCode;
    private PlanCode toPlanCode;
    private Long requiredTopUpAmountCents;
    private PlanChangeRequestStatus status;
    private Long contractId;
    private ContractStatus contractStatus;
    private ZonedDateTime requestedAt;
    private ZonedDateTime appliedAt;
}
