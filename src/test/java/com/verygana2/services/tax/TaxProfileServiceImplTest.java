package com.verygana2.services.tax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import com.verygana2.dtos.tax.TaxProfileDTOs.TaxProfileResponse;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO.ActivityDTO;
import com.verygana2.dtos.tax.TaxProfileSubmitRequestDTO.IncomeReportDTO;
import com.verygana2.dtos.tax.TaxRequestDTOs.SectionReviewRequest;
import com.verygana2.event.TaxProfileChangedEvent;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.commercial.CommercialDocument;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.CommercialActivityType;
import com.verygana2.models.enums.commercial.CommercialDocumentStatus;
import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.tax.IncomeConcept;
import com.verygana2.models.enums.tax.IncomeScope;
import com.verygana2.models.enums.tax.SectionReviewResult;
import com.verygana2.models.enums.tax.TaxCatalogType;
import com.verygana2.models.enums.tax.TaxProfileChangeType;
import com.verygana2.models.enums.tax.TaxProfileSection;
import com.verygana2.models.enums.tax.TaxProfileStatus;
import com.verygana2.models.tax.TaxProfile;
import com.verygana2.models.tax.TaxProfileActivity;
import com.verygana2.models.tax.TaxProfileSectionReview;
import com.verygana2.models.tax.TaxProfileVersion;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialDocumentRepository;
import com.verygana2.repositories.commercial.CommercialOnboardingRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.tax.TaxProfileRepository;
import com.verygana2.repositories.tax.TaxProfileSectionReviewRepository;
import com.verygana2.repositories.tax.TaxProfileVersionRepository;
import com.verygana2.services.UserIdResolver;
import com.verygana2.services.interfaces.declarations.DeclarationService;
import com.verygana2.services.interfaces.tax.CommercialEstablishmentService;
import com.verygana2.services.interfaces.tax.TaxCatalogService;
import com.verygana2.utils.audit.AuditEvent;
import com.verygana2.utils.audit.AuditLevel;

/**
 * Ficha tributaria (4.7 a–h): información declarada y versionada. Enviarla nunca cambia la
 * vocación, la comisión ni la condición tributaria por su cuenta.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaxProfileServiceImpl")
class TaxProfileServiceImplTest {

    private static final long COMMERCIAL_ID = 7L;
    private static final LocalDate LAST_MONTH_START = LocalDate.now(ZoneId.of("America/Bogota")).minusMonths(2).withDayOfMonth(1);
    private static final LocalDate LAST_MONTH_END = LAST_MONTH_START.plusMonths(1).minusDays(1);

    @Mock private TaxProfileRepository profileRepository;
    @Mock private TaxProfileVersionRepository versionRepository;
    @Mock private TaxProfileSectionReviewRepository reviewRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private CommercialOnboardingRepository onboardingRepository;
    @Mock private CommercialDocumentRepository documentRepository;
    @Mock private TaxCatalogService catalogService;
    @Mock private CommercialEstablishmentService establishmentService;
    @Mock private UserIdResolver userIdResolver;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DeclarationService declarationService;

    @InjectMocks private TaxProfileServiceImpl service;

    private CommercialDetails commercial;
    private CommercialOnboarding onboarding;

    @BeforeEach
    void setUp() {
        commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setCommercialActivityType(CommercialActivityType.SERVICES);

        onboarding = new CommercialOnboarding();
        onboarding.setCommercialDetails(commercial);
        onboarding.setDocumentsCompletedAt(ZonedDateTime.now());
        onboarding.setCurrentStep(OnboardingStep.TAX_PROFILE_PENDING);

        lenient().when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
        lenient().when(onboardingRepository.findByCommercialDetails_Id(COMMERCIAL_ID)).thenReturn(Optional.of(onboarding));
        lenient().when(profileRepository.findByCommercial_Id(COMMERCIAL_ID)).thenReturn(Optional.empty());
        lenient().when(profileRepository.save(any(TaxProfile.class))).thenAnswer(inv -> {
            TaxProfile p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(10L);
            }
            return p;
        });
        lenient().when(versionRepository.save(any(TaxProfileVersion.class))).thenAnswer(inv -> {
            TaxProfileVersion v = inv.getArgument(0);
            v.setId(100L + v.getVersionNumber());
            return v;
        });
    }

    private static TaxProfileSubmitRequestDTO validRequest() {
        TaxProfileSubmitRequestDTO dto = new TaxProfileSubmitRequestDTO();
        dto.setActivities(new ArrayList<>(List.of(activity("4711", true))));
        dto.setInvoicingObligated(true);
        dto.setWithholdingAgent(false);
        dto.setIncomeReports(new ArrayList<>(List.of(
                income(IncomeConcept.VENTAS, IncomeScope.EXTERNAL_ONLY, 5_000_000L))));
        dto.setCurrentCapacity("Atiende 50 pedidos diarios con 3 empleados");
        dto.setHasAccountingAdvisor(true);
        dto.setAdvisorScope("Contador externo, declaraciones bimestrales");
        return dto;
    }

    private static ActivityDTO activity(String ciiu, boolean main) {
        ActivityDTO a = new ActivityDTO();
        a.setCiiuCode(ciiu);
        a.setMain(main);
        return a;
    }

    private static IncomeReportDTO income(IncomeConcept concept, IncomeScope scope, long amountCents) {
        IncomeReportDTO r = new IncomeReportDTO();
        r.setConcept(concept);
        r.setScope(scope);
        r.setPeriodStart(LAST_MONTH_START);
        r.setPeriodEnd(LAST_MONTH_END);
        r.setAmountCents(amountCents);
        r.setCurrency("COP");
        return r;
    }

    @Nested
    @DisplayName("submit")
    class Submit {

        @Test
        @DisplayName("primer envío crea la versión 1, avanza el registro al contrato y no toca la vocación")
        void firstSubmission() {
            TaxProfileResponse response = service.submit(COMMERCIAL_ID, validRequest(), "203.0.113.7", "JUnit");

            assertThat(response.currentVersion()).isEqualTo(1);
            assertThat(response.status()).isEqualTo(TaxProfileStatus.SUBMITTED);
            assertThat(response.version().activities()).singleElement()
                    .satisfies(a -> assertThat(a.ciiuCode()).isEqualTo("4711"));
            assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.CONTRACT_PENDING);
            // Positiva/negativa del 1.6 y 4.7 b): la información tributaria no cambia la vocación.
            assertThat(commercial.getCommercialActivityType()).isEqualTo(CommercialActivityType.SERVICES);

            ArgumentCaptor<TaxProfileVersion> saved = ArgumentCaptor.forClass(TaxProfileVersion.class);
            verify(versionRepository).save(saved.capture());
            assertThat(saved.getValue().getSubmittedIp()).isEqualTo("203.0.113.7");
            assertThat(saved.getValue().getSubmittedByUserId()).isEqualTo(COMMERCIAL_ID);

            ArgumentCaptor<Object> events = ArgumentCaptor.forClass(Object.class);
            verify(eventPublisher).publishEvent(events.capture());
            assertThat(events.getValue()).isInstanceOfSatisfying(AuditEvent.class,
                    e -> assertThat(e.getLevel()).isEqualTo(AuditLevel.CRITICAL));
            // Primer envío: no hay cambio de actividad que avisar.
            verify(eventPublisher, never()).publishEvent(any(ApplicationEvent.class));
        }

        @Test
        @DisplayName("sin carga documental completa se rechaza y no crea nada")
        void requiresDocuments() {
            onboarding.setDocumentsCompletedAt(null);

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, validRequest(), null, null))
                    .isInstanceOf(InvalidRequestException.class);
            verify(versionRepository, never()).save(any());
        }

        @Test
        @DisplayName("exige exactamente una actividad principal")
        void exactlyOneMainActivity() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.getActivities().add(activity("5611", true));

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, dto, null, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("principal");
        }

        @Test
        @DisplayName("un total consolidado y las ventas de la plataforma del mismo periodo no se suman dos veces")
        void consolidatedPlusPlatformIsRejected() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setIncomeReports(new ArrayList<>(List.of(
                    income(IncomeConcept.VENTAS, IncomeScope.CONSOLIDATED_INCLUDES_PLATFORM, 9_000_000L),
                    income(IncomeConcept.VENTAS, IncomeScope.PLATFORM_ONLY, 1_000_000L))));

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, dto, null, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("superpuestos");
        }

        @Test
        @DisplayName("ventas externas y de la plataforma del mismo periodo se registran por separado")
        void externalAndPlatformAreAccepted() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setIncomeReports(new ArrayList<>(List.of(
                    income(IncomeConcept.VENTAS, IncomeScope.EXTERNAL_ONLY, 8_000_000L),
                    income(IncomeConcept.VENTAS, IncomeScope.PLATFORM_ONLY, 1_000_000L))));

            TaxProfileResponse response = service.submit(COMMERCIAL_ID, dto, null, null);

            assertThat(response.version().incomeReports()).hasSize(2);
        }

        @Test
        @DisplayName("un periodo futuro es una proyección, no un ingreso: se rechaza")
        void futurePeriodIsRejected() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            IncomeReportDTO future = income(IncomeConcept.INGRESOS, IncomeScope.EXTERNAL_ONLY, 1L);
            future.setPeriodEnd(LocalDate.now().plusMonths(3));
            dto.setIncomeReports(new ArrayList<>(List.of(future)));

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, dto, null, null))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("un código que no está en el catálogo vigente se rechaza")
        void unknownCatalogCodeIsRejected() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setVatConditionCode("INVENTADO");
            // lenient: antes se valida REGIME/INC/etc. con null, que no debe chocar con este stub.
            lenient().doThrow(new InvalidRequestException("no existe")).when(catalogService)
                    .requireActiveCode(TaxCatalogType.VAT_CONDITION, "INVENTADO");

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, dto, null, null))
                    .isInstanceOf(InvalidRequestException.class);
            verify(versionRepository, never()).save(any());
        }

        @Test
        @DisplayName("un RUT que no es del expediente del empresario se rechaza")
        void rutFromAnotherCommercialIsRejected() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setRutDocumentId(55L);
            when(documentRepository.findByIdAndCommercial_Id(55L, COMMERCIAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, dto, null, null))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        @DisplayName("un documento de otro tipo no sirve como RUT")
        void nonRutDocumentIsRejected() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setRutDocumentId(55L);
            CommercialDocument cedula = new CommercialDocument();
            cedula.setId(55L);
            cedula.setDocumentType(CommercialDocumentType.CEDULA_REPRESENTANTE);
            cedula.setStatus(CommercialDocumentStatus.VALIDATED);
            when(documentRepository.findByIdAndCommercial_Id(55L, COMMERCIAL_ID)).thenReturn(Optional.of(cedula));

            assertThatThrownBy(() -> service.submit(COMMERCIAL_ID, dto, null, null))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("RUT");
        }

        @Test
        @DisplayName("sin asesoría, el alcance del asesor no se guarda")
        void noAdvisorClearsScope() {
            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setHasAccountingAdvisor(false);

            TaxProfileResponse response = service.submit(COMMERCIAL_ID, dto, null, null);

            assertThat(response.version().hasAccountingAdvisor()).isFalse();
            assertThat(response.version().advisorScope()).isNull();
        }

        @Test
        @DisplayName("un envío nuevo con otras actividades crea la versión 2 y avisa el cambio, sin tocar la vocación")
        void activityChangePublishesEvent() {
            TaxProfile existing = new TaxProfile();
            existing.setId(10L);
            existing.setCommercial(commercial);
            existing.setCurrentVersion(1);
            existing.setStatus(TaxProfileStatus.REVIEWED);
            when(profileRepository.findByCommercial_Id(COMMERCIAL_ID)).thenReturn(Optional.of(existing));

            TaxProfileVersion v1 = new TaxProfileVersion();
            TaxProfileActivity old = new TaxProfileActivity();
            old.setCiiuCode("4711");
            v1.getActivities().add(old);
            when(versionRepository.findByTaxProfile_IdAndVersionNumber(10L, 1)).thenReturn(Optional.of(v1));

            TaxProfileSubmitRequestDTO dto = validRequest();
            dto.setActivities(new ArrayList<>(List.of(activity("5611", true))));

            TaxProfileResponse response = service.submit(COMMERCIAL_ID, dto, null, null);

            assertThat(response.currentVersion()).isEqualTo(2);
            assertThat(response.status()).isEqualTo(TaxProfileStatus.SUBMITTED);
            assertThat(commercial.getCommercialActivityType()).isEqualTo(CommercialActivityType.SERVICES);
            // TaxProfileChangedEvent es un ApplicationEvent: llega por publishEvent(ApplicationEvent).
            ArgumentCaptor<ApplicationEvent> changes = ArgumentCaptor.forClass(ApplicationEvent.class);
            verify(eventPublisher).publishEvent(changes.capture());
            assertThat(changes.getValue()).isInstanceOfSatisfying(TaxProfileChangedEvent.class,
                    c -> assertThat(c.getChangeType()).isEqualTo(TaxProfileChangeType.ACTIVITY_CHANGE));
        }

        @Test
        @DisplayName("después del registro, editar la ficha no mueve el paso del onboarding")
        void editAfterOnboardingKeepsStep() {
            onboarding.setCurrentStep(OnboardingStep.COMPLETED);

            service.submit(COMMERCIAL_ID, validRequest(), null, null);

            assertThat(onboarding.getCurrentStep()).isEqualTo(OnboardingStep.COMPLETED);
        }
    }

    @Nested
    @DisplayName("reviewSection")
    class ReviewSection {

        private final UUID publicId = UUID.randomUUID();
        private TaxProfile profile;
        private TaxProfileVersion current;

        @BeforeEach
        void setUpProfile() {
            profile = new TaxProfile();
            profile.setId(10L);
            profile.setCommercial(commercial);
            profile.setCurrentVersion(1);
            profile.setStatus(TaxProfileStatus.SUBMITTED);
            current = new TaxProfileVersion();
            current.setId(101L);
            current.setVersionNumber(1);
            current.setTaxProfile(profile);

            when(userIdResolver.toInternalId(publicId)).thenReturn(COMMERCIAL_ID);
            when(profileRepository.findByCommercial_Id(COMMERCIAL_ID)).thenReturn(Optional.of(profile));
            lenient().when(versionRepository.findByTaxProfile_IdAndVersionNumber(10L, 1)).thenReturn(Optional.of(current));
        }

        private SectionReviewRequest request(SectionReviewResult result, String notes) {
            SectionReviewRequest r = new SectionReviewRequest();
            r.setResult(result);
            r.setNotes(notes);
            return r;
        }

        private TaxProfileSectionReview review(TaxProfileSection section, SectionReviewResult result) {
            TaxProfileSectionReview r = new TaxProfileSectionReview();
            r.setSection(section);
            r.setResult(result);
            r.setReviewedAt(ZonedDateTime.now());
            return r;
        }

        @Test
        @DisplayName("una observación sin nota se rechaza: el empresario no sabría qué aclarar")
        void observedRequiresNotes() {
            assertThatThrownBy(() -> service.reviewSection(publicId, TaxProfileSection.A_RUT, 99L,
                    request(SectionReviewResult.OBSERVED, " ")))
                    .isInstanceOf(InvalidRequestException.class);
            verify(reviewRepository, never()).save(any());
        }

        @Test
        @DisplayName("una sección observada deja la ficha OBSERVED y no cambia la vocación")
        void observedSectionMarksProfileObserved() {
            when(reviewRepository.findByVersion_IdOrderByReviewedAtDescIdDesc(101L))
                    .thenReturn(List.of(review(TaxProfileSection.A_RUT, SectionReviewResult.OBSERVED)));

            service.reviewSection(publicId, TaxProfileSection.A_RUT, 99L,
                    request(SectionReviewResult.OBSERVED, "El RUT es de otro titular"));

            assertThat(profile.getStatus()).isEqualTo(TaxProfileStatus.OBSERVED);
            assertThat(commercial.getCommercialActivityType()).isEqualTo(CommercialActivityType.SERVICES);
            verify(reviewRepository).save(any(TaxProfileSectionReview.class));
            verify(profileRepository).save(eq(profile));
        }

        @Test
        @DisplayName("con las 8 secciones revisadas sin observaciones la ficha queda REVIEWED")
        void allSectionsReviewed() {
            List<TaxProfileSectionReview> all = new ArrayList<>();
            for (TaxProfileSection s : TaxProfileSection.values()) {
                all.add(review(s, s == TaxProfileSection.H_ADVISORY
                        ? SectionReviewResult.NOT_APPLICABLE : SectionReviewResult.VERIFIED));
            }
            when(reviewRepository.findByVersion_IdOrderByReviewedAtDescIdDesc(101L)).thenReturn(all);

            service.reviewSection(publicId, TaxProfileSection.H_ADVISORY, 99L,
                    request(SectionReviewResult.NOT_APPLICABLE, null));

            assertThat(profile.getStatus()).isEqualTo(TaxProfileStatus.REVIEWED);
        }

        @Test
        @DisplayName("la revisión más reciente de una sección reemplaza a la anterior (observación subsanada)")
        void latestReviewWins() {
            List<TaxProfileSectionReview> newestFirst = List.of(
                    review(TaxProfileSection.A_RUT, SectionReviewResult.VERIFIED),
                    review(TaxProfileSection.A_RUT, SectionReviewResult.OBSERVED));
            when(reviewRepository.findByVersion_IdOrderByReviewedAtDescIdDesc(101L)).thenReturn(newestFirst);

            service.reviewSection(publicId, TaxProfileSection.A_RUT, 99L,
                    request(SectionReviewResult.VERIFIED, null));

            assertThat(profile.getStatus()).isEqualTo(TaxProfileStatus.SUBMITTED);
        }
    }
}
