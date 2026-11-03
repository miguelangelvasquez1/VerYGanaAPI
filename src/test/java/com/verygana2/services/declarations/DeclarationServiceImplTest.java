package com.verygana2.services.declarations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.declarations.DeclarationDTOs.DeclarationView;
import com.verygana2.dtos.declarations.DeclarationDTOs.Manifestation;
import com.verygana2.dtos.user.commercial.onboarding.ContractApproveRequestDTO;
import com.verygana2.event.FormalizationManifestationRecordedEvent;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.declarations.DeclarationDisclosure;
import com.verygana2.models.declarations.DeclarationManifestation;
import com.verygana2.models.declarations.DeclarationText;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.DisclosureContext;
import com.verygana2.models.enums.declarations.ManifestationDecision;
import com.verygana2.models.enums.declarations.ManifestationStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.declarations.DeclarationDisclosureRepository;
import com.verygana2.repositories.declarations.DeclarationManifestationRepository;
import com.verygana2.repositories.declarations.DeclarationTextRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.services.UserIdResolver;

/**
 * Compromiso de formalización (1.7 / 4.7): una sola manifestación expresa, con casilla no
 * premarcada, en la Hoja Final. Ausencia, negativa y falla técnica quedan diferenciadas.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DeclarationServiceImpl")
class DeclarationServiceImplTest {

    private static final long COMMERCIAL_ID = 7L;

    @Mock private DeclarationTextRepository textRepository;
    @Mock private DeclarationDisclosureRepository disclosureRepository;
    @Mock private DeclarationManifestationRepository manifestationRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private UserIdResolver userIdResolver;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private DeclarationServiceImpl service;

    private DeclarationText text;
    private CommercialDetails commercial;
    private CommercialOnboarding onboarding;
    private CommercialContract contract;

    @BeforeEach
    void setUp() {
        text = new DeclarationText();
        text.setId(1L);
        text.setCode(DeclarationCode.FORMALIZATION_COMMITMENT);
        text.setVersion("1");
        text.setFullText("Reconozco que mi participación en VERYGANA ... cuando legalmente corresponda.");
        text.setTextHash("abc");
        text.setActive(true);

        commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setLegalRepDocType(DocumentType.CC);
        commercial.setLegalRepDocNumber("1094000111");

        onboarding = new CommercialOnboarding();
        onboarding.setLegalRepFirstName("Ana ");
        onboarding.setLegalRepLastName("Pérez");

        contract = new CommercialContract();
        contract.setId(30L);
        contract.setVersion(2);
        contract.setCommercial(commercial);

        lenient().when(textRepository.findFirstByCodeAndActiveTrueOrderByPublishedAtDesc(DeclarationCode.FORMALIZATION_COMMITMENT))
                .thenReturn(Optional.of(text));
        lenient().when(manifestationRepository.save(any(DeclarationManifestation.class))).thenAnswer(inv -> {
            DeclarationManifestation m = inv.getArgument(0);
            m.setId(500L);
            return m;
        });
    }

    private ContractApproveRequestDTO request(ManifestationDecision decision, String version) {
        ContractApproveRequestDTO r = new ContractApproveRequestDTO();
        r.setDecision(decision);
        r.setDeclarationVersion(version);
        return r;
    }

    private Manifestation record(ContractApproveRequestDTO request, String key) {
        return service.recordContractManifestation(onboarding, contract, request, key, COMMERCIAL_ID,
                "203.0.113.7", "JUnit");
    }

    @Test
    @DisplayName("una acción expresa sobre el texto vigente queda MANIFESTED con actor, texto, versión y contrato")
    void expressAcceptance() {
        Manifestation m = record(request(ManifestationDecision.ACCEPT, "1"), "k-1");

        assertThat(m.status()).isEqualTo(ManifestationStatus.MANIFESTED);
        assertThat(m.declarationVersion()).isEqualTo("1");
        assertThat(m.contractId()).isEqualTo(30L);
        assertThat(m.contractVersion()).isEqualTo(2);
        assertThat(m.actorLegalRepName()).isEqualTo("Ana Pérez");

        ArgumentCaptor<DeclarationManifestation> saved = ArgumentCaptor.forClass(DeclarationManifestation.class);
        verify(manifestationRepository).save(saved.capture());
        assertThat(saved.getValue().getActorUserId()).isEqualTo(COMMERCIAL_ID);
        assertThat(saved.getValue().getActorLegalRepDocNumber()).isEqualTo("1094000111");
        assertThat(saved.getValue().getIpAddress()).isEqualTo("203.0.113.7");
        assertThat(saved.getValue().getDeclarationText()).isSameAs(text);
    }

    @Test
    @DisplayName("sin acción (casilla sin marcar o sin cuerpo) queda NOT_MANIFESTED, nunca aceptación")
    void silenceIsNotAcceptance() {
        assertThat(record(null, null).status()).isEqualTo(ManifestationStatus.NOT_MANIFESTED);
    }

    @Test
    @DisplayName("una negativa expresa queda EXPRESS_REFUSAL con su motivo, y se avisa para abrir aclaración")
    void expressRefusal() {
        ContractApproveRequestDTO r = request(ManifestationDecision.REFUSE, "1");
        r.setRefusalReason("  No estoy de acuerdo  ");

        Manifestation m = record(r, null);

        assertThat(m.status()).isEqualTo(ManifestationStatus.EXPRESS_REFUSAL);
        assertThat(m.detail()).isEqualTo("No estoy de acuerdo");
        ArgumentCaptor<ApplicationEvent> event = ArgumentCaptor.forClass(ApplicationEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue()).isInstanceOfSatisfying(FormalizationManifestationRecordedEvent.class,
                e -> assertThat(e.getStatus()).isEqualTo(ManifestationStatus.EXPRESS_REFUSAL));
    }

    @Test
    @DisplayName("una acción sobre otra versión del texto es una falla técnica, no aceptación ni negativa")
    void staleVersionIsTechnicalIncident() {
        assertThat(record(request(ManifestationDecision.ACCEPT, "0"), null).status())
                .isEqualTo(ManifestationStatus.TECHNICAL_INCIDENT);
        assertThat(record(request(ManifestationDecision.REFUSE, null), null).status())
                .isEqualTo(ManifestationStatus.TECHNICAL_INCIDENT);
    }

    @Test
    @DisplayName("un reintento con la misma clave conserva una sola evidencia")
    void retryWithSameKeyKeepsOneEvidence() {
        DeclarationManifestation first = new DeclarationManifestation();
        first.setId(500L);
        first.setStatus(ManifestationStatus.MANIFESTED);
        first.setDeclarationText(text);
        first.setContract(contract);
        first.setContractVersion(2);
        when(manifestationRepository.findByIdempotencyKey("k-1")).thenReturn(Optional.of(first));

        Manifestation m = record(request(ManifestationDecision.REFUSE, "1"), "k-1");

        assertThat(m.id()).isEqualTo(500L);
        assertThat(m.status()).isEqualTo(ManifestationStatus.MANIFESTED);
        verify(manifestationRepository, never()).save(any());
    }

    @Test
    @DisplayName("si ya se manifestó en este contrato, no se pide ni registra otra aceptación")
    void noSecondAcceptanceInSameProcess() {
        DeclarationManifestation existing = new DeclarationManifestation();
        existing.setId(400L);
        existing.setStatus(ManifestationStatus.MANIFESTED);
        existing.setDeclarationText(text);
        existing.setContract(contract);
        existing.setContractVersion(1);
        when(manifestationRepository.findFirstByContract_IdAndCodeAndDeclarationText_IdAndStatus(
                30L, DeclarationCode.FORMALIZATION_COMMITMENT, 1L, ManifestationStatus.MANIFESTED))
                .thenReturn(Optional.of(existing));

        Manifestation m = record(request(ManifestationDecision.ACCEPT, "1"), "k-2");

        assertThat(m.id()).isEqualTo(400L);
        verify(manifestationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
    }

    @Test
    @DisplayName("consultar el texto registra la puesta a disposición y devuelve la evidencia existente, sin aceptar")
    void viewingDoesNotAccept() {
        when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
        DeclarationManifestation existing = new DeclarationManifestation();
        existing.setId(400L);
        existing.setStatus(ManifestationStatus.MANIFESTED);
        existing.setDeclarationText(text);
        existing.setContract(contract);
        existing.setOccurredAt(ZonedDateTime.now());
        when(manifestationRepository.findByCommercial_IdAndCodeOrderByOccurredAtDescIdDesc(
                COMMERCIAL_ID, DeclarationCode.FORMALIZATION_COMMITMENT)).thenReturn(List.of(existing));

        DeclarationView view = service.viewForCommercial(COMMERCIAL_ID, DeclarationCode.FORMALIZATION_COMMITMENT,
                DisclosureContext.TAX_PROFILE);

        assertThat(view.fullText()).isEqualTo(text.getFullText());
        assertThat(view.manifestation().id()).isEqualTo(400L);
        verify(disclosureRepository).save(any(DeclarationDisclosure.class));
        verify(manifestationRepository, never()).save(any());
    }

    @Test
    @DisplayName("la manifestación vigente es la expresa aunque después haya otros registros")
    void currentPrefersExpressManifestation() {
        DeclarationManifestation later = new DeclarationManifestation();
        later.setId(2L);
        later.setStatus(ManifestationStatus.NOT_MANIFESTED);
        later.setDeclarationText(text);
        later.setContract(contract);
        DeclarationManifestation earlier = new DeclarationManifestation();
        earlier.setId(1L);
        earlier.setStatus(ManifestationStatus.MANIFESTED);
        earlier.setDeclarationText(text);
        earlier.setContract(contract);
        when(manifestationRepository.findByCommercial_IdAndCodeOrderByOccurredAtDescIdDesc(
                eq(COMMERCIAL_ID), eq(DeclarationCode.FORMALIZATION_COMMITMENT))).thenReturn(List.of(later, earlier));

        assertThat(service.currentManifestation(COMMERCIAL_ID, DeclarationCode.FORMALIZATION_COMMITMENT).id())
                .isEqualTo(1L);
        // Leer la evidencia desde otro módulo no registra una puesta a disposición nueva.
        verify(disclosureRepository, never()).save(any());
    }
}
