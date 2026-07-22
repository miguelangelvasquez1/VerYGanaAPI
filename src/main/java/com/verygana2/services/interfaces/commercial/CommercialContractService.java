package com.verygana2.services.interfaces.commercial;

import java.util.List;

import com.verygana2.dtos.user.commercial.onboarding.ContractReviewListItemDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.commercial.OnboardingStep;

/** Pasos 9-11: generación documental, revisión del empresario y revisión de VERYGANA. */
public interface CommercialContractService {

    // ---- Lado comercial (pasos 9-10) ----

    ContractSummaryResponseDTO generate(Long userId);

    ContractSummaryResponseDTO getCurrent(Long userId);

    ContractSummaryResponseDTO businessApprove(Long userId);

    /**
     * El empresario regresa a corregir campos no jurídicos (diagnóstico/plan/documentos).
     * Devuelve el paso al que quedó el onboarding para que el front redirija ahí directamente.
     */
    OnboardingStep requestChanges(Long userId);

    // ---- Lado VERYGANA / compliance (paso 11) ----

    /**
     * Listado de contratos para el panel de compliance. statusFilter null trae todos los
     * relevantes para VERYGANA (PENDING_VERYGANA_REVIEW, APPROVED, REJECTED — no incluye
     * PENDING_BUSINESS_REVIEW porque todavía no llega a VERYGANA); con un valor filtra a
     * ese estado únicamente.
     */
    List<ContractReviewListItemDTO> listContracts(ContractStatus statusFilter);

    ContractSummaryResponseDTO getForReview(Long contractId);

    ContractSummaryResponseDTO approve(Long contractId, Long reviewerUserId);

    /**
     * documentsIssue=true habilita autoservicio (el onboarding vuelve a DOCUMENTS_PENDING
     * para que el comercial corrija documentos y regenere el contrato). documentsIssue=false
     * no reabre ningún paso — el motivo no es corregible por autoservicio, requiere que
     * VERYGANA contacte directamente al comercial.
     */
    ContractSummaryResponseDTO reject(Long contractId, Long reviewerUserId, String reason, boolean documentsIssue);

    /**
     * Registra la firma del contrato. Mientras no exista un proveedor real de firma
     * electrónica, este es el único punto de entrada para completar el flujo (lo
     * llamará compliance manualmente); en producción lo reemplaza el webhook del proveedor.
     */
    ContractSummaryResponseDTO markSigned(Long contractId);
}
