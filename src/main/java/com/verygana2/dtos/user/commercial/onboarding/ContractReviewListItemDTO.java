package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.ContractStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del listado de contratos revisados/por revisar por VERYGANA (ROLE_COMPLIANCE_OFFICER). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractReviewListItemDTO {
    private Long contractId;
    private Long userId;
    private String companyName;
    private String email;
    private CommercialRoute route;
    private int version;
    private ContractStatus status;
    private ZonedDateTime generatedAt;
    private ZonedDateTime businessApprovedAt;

    /** Fecha de la decisión de VERYGANA (aprobación o rechazo). Null mientras esté PENDING_VERYGANA_REVIEW. */
    private ZonedDateTime veryganaReviewedAt;

    /** Fecha de firma del contrato. Null mientras no esté SIGNED. */
    private ZonedDateTime esignatureSignedAt;
}
