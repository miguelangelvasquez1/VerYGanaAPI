package com.verygana2.services.commercial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.dtos.user.commercial.expediente.DocumentReviewRequestDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentDTO;
import com.verygana2.dtos.user.commercial.expediente.ExpedienteDocumentUploadRequestDTO;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.SupportedMimeType;
import com.verygana2.models.enums.commercial.CommercialDocumentReviewStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.storage.service.R2Service;

/**
 * Expediente documental (4.7 / 4.8): reemplazar conserva el historial, el vencimiento solo
 * existe con fecha comprobada y no se acepta un documento de otro titular.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialExpedienteDocumentServiceImpl")
class CommercialExpedienteDocumentServiceImplTest {

    private static final long COMMERCIAL_ID = 7L;

    @Mock private CommercialDocumentRepository documentRepository;
    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private R2Service r2Service;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CommercialExpedienteDocumentServiceImpl service;

    private CommercialDetails commercial;
    private CommercialOnboarding onboarding;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxSizeBytes", 10_000_000L);
        commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setNit("900123456");
        commercial.setLegalRepDocNumber("1094000111");

        onboarding = new CommercialOnboarding();
        onboarding.setId(3L);
        onboarding.setCommercialDetails(commercial);
        onboarding.setCurrentStep(OnboardingStep.COMPLETED);

        lenient().when(onboardingRepository.findByCommercialDetails_Id(COMMERCIAL_ID)).thenReturn(Optional.of(onboarding));
        lenient().when(documentRepository.save(any(CommercialDocument.class))).thenAnswer(inv -> {
            CommercialDocument d = inv.getArgument(0);
            if (d.getId() == null) {
                d.setId(50L);
            }
            return d;
        });
    }

    private CommercialDocument document(long id, CommercialDocumentType type, CommercialDocumentStatus status) {
        CommercialDocument d = new CommercialDocument();
        d.setId(id);
        d.setCommercial(commercial);
        d.setOnboarding(onboarding);
        d.setDocumentType(type);
        d.setStatus(status);
        d.setObjectKey("commercial-documents/3/" + id);
        d.setSizeBytes(1000L);
        return d;
    }

    private ExpedienteDocumentUploadRequestDTO upload(CommercialDocumentType type, Long replaces) {
        ExpedienteDocumentUploadRequestDTO dto = new ExpedienteDocumentUploadRequestDTO();
        dto.setDocumentType(type);
        dto.setOriginalFileName("rut.pdf");
        dto.setContentType("application/pdf");
        dto.setSizeBytes(1000L);
        dto.setReplacesDocumentId(replaces);
        return dto;
    }

    @Nested
    @DisplayName("carga y reemplazo")
    class Upload {

        @Test
        @DisplayName("durante el onboarding no se usa el expediente")
        void blockedBeforeOnboardingCompleted() {
            onboarding.setCurrentStep(OnboardingStep.DOCUMENTS_PENDING);

            assertThatThrownBy(() -> service.prepareUpload(COMMERCIAL_ID, upload(CommercialDocumentType.RUT, null)))
                    .isInstanceOf(InvalidRequestException.class);
            verify(documentRepository, never()).save(any());
        }

        @Test
        @DisplayName("el reemplazo debe ser del mismo tipo que el documento vigente")
        void replacementMustMatchType() {
            CommercialDocument rut = document(10L, CommercialDocumentType.RUT, CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findByIdAndCommercial_Id(10L, COMMERCIAL_ID)).thenReturn(Optional.of(rut));

            assertThatThrownBy(() -> service.prepareUpload(COMMERCIAL_ID,
                    upload(CommercialDocumentType.CAMARA_COMERCIO, 10L)))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("confirmar un reemplazo deja el anterior SUPERSEDED en el historial, sin borrarlo de storage")
        void replacementKeepsHistory() {
            CommercialDocument old = document(10L, CommercialDocumentType.RUT, CommercialDocumentStatus.VALIDATED);
            CommercialDocument fresh = document(11L, CommercialDocumentType.RUT, CommercialDocumentStatus.PENDING);
            fresh.setReplacesDocument(old);
            when(documentRepository.findByIdAndCommercial_Id(11L, COMMERCIAL_ID)).thenReturn(Optional.of(fresh));
            when(r2Service.validateUploadedObject(eq(true), anyString(), anyLong(), anyLong(), any()))
                    .thenReturn(SupportedMimeType.APPLICATION_PDF);

            ExpedienteDocumentDTO result = service.confirmUpload(COMMERCIAL_ID, 11L);

            assertThat(result.status()).isEqualTo(CommercialDocumentStatus.VALIDATED);
            assertThat(result.replacesDocumentId()).isEqualTo(10L);
            assertThat(old.getStatus()).isEqualTo(CommercialDocumentStatus.SUPERSEDED);
            assertThat(old.getSupersededAt()).isNotNull();
            verify(r2Service, never()).deleteObject(anyString());
        }

        @Test
        @DisplayName("un documento ya confirmado no se confirma de nuevo")
        void confirmTwiceIsRejected() {
            CommercialDocument done = document(11L, CommercialDocumentType.RUT, CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findByIdAndCommercial_Id(11L, COMMERCIAL_ID)).thenReturn(Optional.of(done));

            assertThatThrownBy(() -> service.confirmUpload(COMMERCIAL_ID, 11L))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    @DisplayName("revisión de compliance")
    class Review {

        private DocumentReviewRequestDTO review(CommercialDocumentReviewStatus result, String holder, String notes) {
            DocumentReviewRequestDTO dto = new DocumentReviewRequestDTO();
            dto.setResult(result);
            dto.setHolderIdentification(holder);
            dto.setNotes(notes);
            return dto;
        }

        @Test
        @DisplayName("no se acepta un RUT de otro titular")
        void rejectsOtherHolder() {
            CommercialDocument rut = document(10L, CommercialDocumentType.RUT, CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findById(10L)).thenReturn(Optional.of(rut));

            assertThatThrownBy(() -> service.review(10L, 99L,
                    review(CommercialDocumentReviewStatus.ACCEPTED, "800555444-1", null)))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("titular");
            assertThat(rut.getReviewStatus()).isNull();
        }

        @Test
        @DisplayName("acepta el RUT del empresario aunque venga con puntos y dígito de verificación")
        void acceptsOwnNitWithCheckDigit() {
            CommercialDocument rut = document(10L, CommercialDocumentType.RUT, CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findById(10L)).thenReturn(Optional.of(rut));

            ExpedienteDocumentDTO result = service.review(10L, 99L,
                    review(CommercialDocumentReviewStatus.ACCEPTED, "900.123.456-7", null));

            assertThat(result.reviewStatus()).isEqualTo(CommercialDocumentReviewStatus.ACCEPTED);
            assertThat(rut.getReviewedByUserId()).isEqualTo(99L);
        }

        @Test
        @DisplayName("la cédula se compara contra el representante legal")
        void cedulaMatchesLegalRep() {
            CommercialDocument cedula = document(12L, CommercialDocumentType.CEDULA_REPRESENTANTE,
                    CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findById(12L)).thenReturn(Optional.of(cedula));

            assertThatThrownBy(() -> service.review(12L, 99L,
                    review(CommercialDocumentReviewStatus.ACCEPTED, "900123456", null)))
                    .isInstanceOf(InvalidRequestException.class);
            assertThat(service.review(12L, 99L,
                    review(CommercialDocumentReviewStatus.ACCEPTED, "1.094.000.111", null)).reviewStatus())
                    .isEqualTo(CommercialDocumentReviewStatus.ACCEPTED);
        }

        @Test
        @DisplayName("observar o rechazar exige una nota")
        void observedRequiresNotes() {
            CommercialDocument rut = document(10L, CommercialDocumentType.RUT, CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findById(10L)).thenReturn(Optional.of(rut));

            assertThatThrownBy(() -> service.review(10L, 99L,
                    review(CommercialDocumentReviewStatus.OBSERVED, null, null)))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("sin fecha de vencimiento el documento nunca figura vencido")
        void noExpiryNeverExpired() {
            CommercialDocument permit = document(13L, CommercialDocumentType.PERMISO_SECTORIAL,
                    CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findById(13L)).thenReturn(Optional.of(permit));

            ExpedienteDocumentDTO result = service.review(13L, 99L,
                    review(CommercialDocumentReviewStatus.ACCEPTED, null, null));

            assertThat(result.expiresAt()).isNull();
            assertThat(result.expired()).isFalse();
        }

        @Test
        @DisplayName("con fecha de vencimiento pasada figura vencido")
        void pastExpiryIsExpired() {
            CommercialDocument permit = document(13L, CommercialDocumentType.PERMISO_SECTORIAL,
                    CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findById(13L)).thenReturn(Optional.of(permit));
            DocumentReviewRequestDTO dto = review(CommercialDocumentReviewStatus.ACCEPTED, null, null);
            dto.setIssuedAt(LocalDate.now().minusYears(2));
            dto.setExpiresAt(LocalDate.now().minusDays(1));

            assertThat(service.review(13L, 99L, dto).expired()).isTrue();
        }

        @Test
        @DisplayName("un documento reemplazado no se revisa")
        void supersededIsNotReviewed() {
            CommercialDocument old = document(10L, CommercialDocumentType.RUT, CommercialDocumentStatus.SUPERSEDED);
            when(documentRepository.findById(10L)).thenReturn(Optional.of(old));

            assertThatThrownBy(() -> service.review(10L, 99L,
                    review(CommercialDocumentReviewStatus.ACCEPTED, "900123456", null)))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }
}
