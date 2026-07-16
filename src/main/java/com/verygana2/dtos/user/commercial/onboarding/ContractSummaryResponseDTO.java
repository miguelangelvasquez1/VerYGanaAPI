package com.verygana2.dtos.user.commercial.onboarding;

import java.time.ZonedDateTime;

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
    private String downloadUrl;
}
