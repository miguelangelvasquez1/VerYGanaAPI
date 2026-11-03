package com.verygana2.services.inforequests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingRequest;
import com.verygana2.dtos.inforequests.InformationRequestDTOs.FindingView;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.enums.AccountStatus;
import com.verygana2.models.enums.declarations.ManifestationStatus;
import com.verygana2.models.enums.inforequests.ContractualMeasure;
import com.verygana2.models.enums.inforequests.FindingClassification;
import com.verygana2.models.enums.inforequests.FindingSeverity;
import com.verygana2.models.enums.inforequests.InformationRequestStatus;
import com.verygana2.models.inforequests.ComplianceFinding;
import com.verygana2.models.inforequests.InformationRequest;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.declarations.DeclarationManifestationRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.inforequests.ComplianceFindingRepository;
import com.verygana2.repositories.inforequests.InformationRequestRepository;
import com.verygana2.repositories.inforequests.TechnicalIncidentRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.AccountStatusService;

/**
 * Hallazgos (4.8): cada medida necesita hechos, valoración y fundamento. Una inconsistencia
 * aislada no es fraude y una ausencia de manifestación no es una negativa.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ComplianceCaseServiceImpl")
class ComplianceCaseServiceImplTest {

    private static final long COMMERCIAL_ID = 7L;
    private static final UUID COMMERCIAL_PUBLIC_ID = new UUID(0, COMMERCIAL_ID);

    @Mock private ComplianceFindingRepository findingRepository;
    @Mock private TechnicalIncidentRepository incidentRepository;
    @Mock private InformationRequestRepository requestRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private DeclarationManifestationRepository manifestationRepository;
    @Mock private AccountStatusService accountStatusService;
    @Mock private UserIdResolver userIdResolver;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private ComplianceCaseServiceImpl service;

    @BeforeEach
    void setUp() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        lenient().when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
        lenient().when(userIdResolver.toInternalId(COMMERCIAL_PUBLIC_ID)).thenReturn(COMMERCIAL_ID);
        lenient().when(findingRepository.save(any(ComplianceFinding.class))).thenAnswer(inv -> {
            ComplianceFinding f = inv.getArgument(0);
            f.setPublicId(UUID.randomUUID());
            return f;
        });
    }

    private FindingRequest finding(FindingClassification classification, ContractualMeasure measure) {
        FindingRequest dto = new FindingRequest();
        dto.setCommercialPublicId(COMMERCIAL_PUBLIC_ID);
        dto.setFactObserved("La dirección declarada difiere del RUT");
        dto.setClassification(classification);
        dto.setSeverity(FindingSeverity.LOW);
        dto.setImpact("Ninguna operación afectada");
        dto.setRemediable(true);
        dto.setContractualBasis("Contrato B, numeral 4.8");
        dto.setMeasure(measure);
        return dto;
    }

    @Test
    @DisplayName("una inconsistencia aislada no puede suspender")
    void isolatedInconsistencyCannotSuspend() {
        assertThatThrownBy(() -> service.recordFinding(
                finding(FindingClassification.ISOLATED_INCONSISTENCY, ContractualMeasure.SUSPENSION), 99L))
                .isInstanceOf(InvalidRequestException.class);
        verify(accountStatusService, never()).transition(any(), any(), anyString(), anyString());
        verify(findingRepository, never()).save(any());
    }

    @Test
    @DisplayName("un error subsanable se registra con advertencia y no toca la cuenta")
    void subsanableErrorWithWarning() {
        FindingView view = service.recordFinding(
                finding(FindingClassification.SUBSANABLE_ERROR, ContractualMeasure.WARNING), 99L);

        assertThat(view.accountStatusApplied()).isNull();
        verify(accountStatusService, never()).transition(any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("una negativa solo se clasifica así si hay una negativa expresa registrada")
    void refusalRequiresEvidence() {
        when(manifestationRepository.existsByCommercial_IdAndStatus(COMMERCIAL_ID, ManifestationStatus.EXPRESS_REFUSAL))
                .thenReturn(false);

        assertThatThrownBy(() -> service.recordFinding(
                finding(FindingClassification.EXPRESS_REFUSAL, ContractualMeasure.NONE), 99L))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("una falsedad verificada con suspensión cambia el estado con historial y cierra el requerimiento")
    void verifiedFalseInfoSuspends() {
        InformationRequest request = new InformationRequest();
        request.setPublicId(UUID.randomUUID());
        request.setStatus(InformationRequestStatus.RESPONDED);
        request.setOpenDedupKey("K");
        when(requestRepository.findByPublicIdAndCommercial_Id(request.getPublicId(), COMMERCIAL_ID))
                .thenReturn(Optional.of(request));
        FindingRequest dto = finding(FindingClassification.VERIFIED_FALSE_INFO, ContractualMeasure.SUSPENSION);
        dto.setRequestPublicId(request.getPublicId());
        dto.setSeverity(FindingSeverity.HIGH);
        dto.setRemediable(false);

        FindingView view = service.recordFinding(dto, 99L);

        assertThat(view.accountStatusApplied()).isEqualTo(AccountStatus.SUSPENDED);
        verify(accountStatusService).transition(eq(COMMERCIAL_ID), eq(AccountStatus.SUSPENDED), anyString(),
                eq("COMPLIANCE_OFFICER:99"));
        assertThat(request.getStatus()).isEqualTo(InformationRequestStatus.CLOSED_WITH_FINDING);
    }

    @Test
    @DisplayName("remitir a terminación no cambia el estado de la cuenta por sí solo")
    void terminationReferralDoesNotTransition() {
        service.recordFinding(finding(FindingClassification.VERIFIED_FALSE_INFO, ContractualMeasure.TERMINATION_REFERRAL), 99L);

        verify(accountStatusService, never()).transition(any(), any(), anyString(), anyString());
    }
}
