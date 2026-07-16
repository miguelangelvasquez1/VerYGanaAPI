package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

import com.verygana2.models.enums.commercial.CommercialRoute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Fila del listado de contratos pendientes de revisión de VERYGANA (ROLE_COMPLIANCE_OFFICER). */
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
    private ZonedDateTime generatedAt;
    private ZonedDateTime businessApprovedAt;
}
