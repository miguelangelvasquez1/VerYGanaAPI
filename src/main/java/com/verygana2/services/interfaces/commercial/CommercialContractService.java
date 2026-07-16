package com.verygana2.services.interfaces.commercial;

import java.util.List;

import com.verygana2.dtos.user.commercial.onboarding.ContractReviewListItemDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;

/** Pasos 9-11: generación documental, revisión del empresario y revisión de VERYGANA. */
public interface CommercialContractService {

    // ---- Lado comercial (pasos 9-10) ----

    ContractSummaryResponseDTO generate(Long userId);

    ContractSummaryResponseDTO getCurrent(Long userId);

    ContractSummaryResponseDTO businessApprove(Long userId);

    /** El empresario regresa a corregir campos no jurídicos (diagnóstico/plan/documentos). */
    void requestChanges(Long userId);

    // ---- Lado VERYGANA / compliance (paso 11) ----

    List<ContractReviewListItemDTO> listPendingReview();

    ContractSummaryResponseDTO getForReview(Long contractId);

    ContractSummaryResponseDTO approve(Long contractId, Long reviewerUserId);

    ContractSummaryResponseDTO reject(Long contractId, Long reviewerUserId, String reason);
}
