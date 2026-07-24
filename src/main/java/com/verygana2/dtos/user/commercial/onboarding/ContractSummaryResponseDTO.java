package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;
import java.util.List;

import com.verygana2.models.enums.commercial.ContractStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractSummaryResponseDTO {
    private Long contractId;
    private int version;
    private ContractStatus status;
    private ZonedDateTime generatedAt;
    private ZonedDateTime businessApprovedAt;
    private ZonedDateTime veryganaReviewedAt;
    private String veryganaDecisionNotes;
    private ZonedDateTime esignatureSentAt;
    private ZonedDateTime esignatureSignedAt;
    private String downloadUrl;

    /** Documentos (no descartados) cargados por el comercial dueño de este contrato. */
    private List<CommercialDocumentResponseDTO> documents;
}
