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
    /**
     * URL pre-firmada (TTL corto) del PDF del otrosí, para que el comercial lo lea antes
     * de aprobarlo. null si aún no se generó el contrato o si fue cancelado.
     */
    private String contractDownloadUrl;
    private ZonedDateTime requestedAt;
    private ZonedDateTime appliedAt;
    /** Motivo del rechazo de VerYGana — solo presente cuando status es REJECTED. */
    private String rejectionReason;
    /** Fecha en que el comercial dio por leído el rechazo (null si aún no lo ha hecho). */
    private ZonedDateTime rejectionAcknowledgedAt;
}
