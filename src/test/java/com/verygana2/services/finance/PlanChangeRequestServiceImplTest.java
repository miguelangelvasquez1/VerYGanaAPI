package com.verygana2.services.finance;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.finance.plans.responses.PlanChangePreviewResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.event.ContractSignedEvent;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.User;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.PlanRepository;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.commercial.CommercialContractService;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanChangeRequestServiceImpl}: validaciones de negocio al solicitar
 * un cambio de plan, el preview de solo lectura, la cancelación, y la reacción a la
 * firma del otrosí (aplicación inmediata vs. bloqueo por abono pendiente).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanChangeRequestServiceImpl")
class PlanChangeRequestServiceImplTest {

    @Mock private PlanChangeRequestRepository planChangeRequestRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private PlanRepository planRepository;
    @Mock private CommercialContractService commercialContractService;
    @Mock private CommercialContractRepository commercialContractRepository;
    @Mock private NotificationService notificationService;

    private PlanChangeRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PlanChangeRequestServiceImpl(planChangeRequestRepository, commercialDetailsRepository,
                planRepository, commercialContractService, commercialContractRepository, notificationService);
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private Plan plan(Long id, PlanCode code, Long monthlyPriceCents, Long minInvestmentCents, Long maxInvestmentCents) {
        Plan p = new Plan();
        p.setId(id);
        p.setCode(code);
        p.setName(code.name());
        p.setActive(true);
        p.setVersion(1);
        p.setMonthlyPriceCents(monthlyPriceCents);
        p.setMinInvestmentCents(minInvestmentCents);
        p.setMaxInvestmentCents(maxInvestmentCents);
        p.setSaleCommissionPct(10);
        p.setMaxKeysPct(20);
        return p;
    }

    private Plan basicPlan() {
        return plan(1L, PlanCode.BASIC, 200_000_00L, null, null);
    }

    private Plan standardPlan() {
        return plan(2L, PlanCode.STANDARD, null, 1_000_000_00L, 9_999_999_00L);
    }

    private Wallet wallet(long balanceCents) {
        Wallet w = new Wallet();
        w.setBalanceCents(balanceCents);
        return w;
    }

    private CommercialDetails commercial(Long id, Plan currentPlan, Wallet wallet) {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(id);
        commercial.setCurrentPlan(currentPlan);
        commercial.setWallet(wallet);
        User user = new User();
        user.setId(id);
        user.setEmail("commercial" + id + "@test.com");
        commercial.setUser(user);
        return commercial;
    }

    // ─── requestPlanChange ──────────────────────────────────────────────────

    @Nested
    @DisplayName("requestPlanChange")
    class RequestPlanChange {

        @Test
        @DisplayName("comercial no encontrado: lanza EntityNotFoundException")
        void commercialNotFound_throwsEntityNotFoundException() {
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.requestPlanChange(1L, PlanCode.STANDARD, 0L))
                    .isInstanceOf(EntityNotFoundException.class);

            verifyNoInteractions(planChangeRequestRepository);
        }

        @Test
        @DisplayName("plan destino no encontrado o inactivo: lanza ValidationException")
        void targetPlanNotFound_throwsValidationException() {
            CommercialDetails commercial = commercial(1L, basicPlan(), wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.requestPlanChange(1L, PlanCode.STANDARD, 0L))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("mismo plan que el actual: lanza ValidationException")
        void samePlanAsCurrent_throwsValidationException() {
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, basic, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));

            assertThatThrownBy(() -> service.requestPlanChange(1L, PlanCode.BASIC, 0L))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("downgrade a BASIC con saldo > 0: lanza ValidationException con el saldo (en pesos) en el mensaje")
        void downgradeToBasicWithBalance_throwsValidationExceptionWithBalance() {
            Plan standard = standardPlan();
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, standard, wallet(500_000L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));

            assertThatThrownBy(() -> service.requestPlanChange(1L, PlanCode.BASIC, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("5000");

            verify(planChangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("downgrade a BASIC con saldo == 0: permite continuar")
        void downgradeToBasicWithZeroBalance_continues() {
            Plan standard = standardPlan();
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, standard, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any())).thenReturn(List.of());
            when(commercialContractRepository.findOpenRechargeContracts(1L)).thenReturn(List.of());
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ContractSummaryResponseDTO contractSummary = new ContractSummaryResponseDTO();
            contractSummary.setContractId(99L);
            when(commercialContractService.generateFor(eq(commercial), eq(ContractPurpose.PLAN_CHANGE), isNull(), eq(basic)))
                    .thenReturn(contractSummary);
            when(commercialContractRepository.findById(99L)).thenReturn(Optional.empty());

            PlanChangeRequest result = service.requestPlanChange(1L, PlanCode.BASIC, null);

            assertThat(result.getStatus()).isEqualTo(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
        }

        @Test
        @DisplayName("solicitud duplicada ya en curso: lanza BusinessException")
        void duplicateRequestInProgress_throwsBusinessException() {
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basicPlan(), wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any()))
                    .thenReturn(List.of(new PlanChangeRequest()));

            assertThatThrownBy(() -> service.requestPlanChange(1L, PlanCode.STANDARD, 1_000_000_00L))
                    .isInstanceOf(BusinessException.class);

            verify(planChangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("recarga/contrato abierto: lanza BusinessException")
        void openRechargeContract_throwsBusinessException() {
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basicPlan(), wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any())).thenReturn(List.of());
            when(commercialContractRepository.findOpenRechargeContracts(1L)).thenReturn(List.of(new CommercialContract()));

            assertThatThrownBy(() -> service.requestPlanChange(1L, PlanCode.STANDARD, 1_000_000_00L))
                    .isInstanceOf(BusinessException.class);

            verify(planChangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("camino feliz completo: guarda dos veces (REQUESTED, luego CONTRACT_PENDING_REVIEW) y vincula el contrato")
        void happyPath_savesTwiceAndLinksContract() {
            Plan basic = basicPlan();
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basic, wallet(500_000_00L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any())).thenReturn(List.of());
            when(commercialContractRepository.findOpenRechargeContracts(1L)).thenReturn(List.of());
            // save() muta y devuelve el MISMO objeto, así que un ArgumentCaptor común capturaría dos
            // referencias al mismo estado final — se toma una foto del status en cada invocación.
            List<PlanChangeRequestStatus> savedStatusesSnapshot = new java.util.ArrayList<>();
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> {
                PlanChangeRequest arg = inv.getArgument(0);
                savedStatusesSnapshot.add(arg.getStatus());
                return arg;
            });

            ContractSummaryResponseDTO contractSummary = new ContractSummaryResponseDTO();
            contractSummary.setContractId(77L);
            when(commercialContractService.generateFor(eq(commercial), eq(ContractPurpose.PLAN_CHANGE), isNull(), eq(standard)))
                    .thenReturn(contractSummary);

            CommercialContract contract = new CommercialContract();
            contract.setId(77L);
            when(commercialContractRepository.findById(77L)).thenReturn(Optional.of(contract));

            PlanChangeRequest result = service.requestPlanChange(1L, PlanCode.STANDARD, 1_500_000_00L);

            verify(planChangeRequestRepository, times(2)).save(any());
            assertThat(savedStatusesSnapshot).containsExactly(
                    PlanChangeRequestStatus.REQUESTED, PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            assertThat(result.getContract()).isEqualTo(contract);
            // STANDARD: el abono es el monto a invertir indicado (dentro del rango [min, max] del plan),
            // independiente del saldo actual del wallet.
            assertThat(result.getRequiredTopUpAmountCents()).isEqualTo(1_500_000_00L);
        }

        @Test
        @DisplayName("computeRequiredTopUp para BASIC retorna monthlyPriceCents")
        void computeRequiredTopUp_basic_returnsMonthlyPrice() {
            Plan standard = standardPlan();
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, standard, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any())).thenReturn(List.of());
            when(commercialContractRepository.findOpenRechargeContracts(1L)).thenReturn(List.of());
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            ContractSummaryResponseDTO contractSummary = new ContractSummaryResponseDTO();
            contractSummary.setContractId(1L);
            lenient().when(commercialContractService.generateFor(any(), any(), any(), any())).thenReturn(contractSummary);
            when(commercialContractRepository.findById(anyLong())).thenReturn(Optional.empty());

            PlanChangeRequest result = service.requestPlanChange(1L, PlanCode.BASIC, null);

            assertThat(result.getRequiredTopUpAmountCents()).isEqualTo(basic.getMonthlyPriceCents());
        }
    }

    // ─── previewPlanChange ──────────────────────────────────────────────────

    @Nested
    @DisplayName("previewPlanChange")
    class PreviewPlanChange {

        @Test
        @DisplayName("no tiene efectos secundarios")
        void noSideEffects() {
            Plan basic = basicPlan();
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basic, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            service.previewPlanChange(1L, PlanCode.STANDARD, null);

            verify(planChangeRequestRepository, never()).save(any());
            verifyNoInteractions(commercialContractService, commercialContractRepository, notificationService);
        }

        @Test
        @DisplayName("downgrade a BASIC elegible (saldo en 0): eligible=true y mensaje correspondiente")
        void downgradeToBasicEligible() {
            Plan standard = standardPlan();
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, standard, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.BASIC, null);

            assertThat(dto.isEligible()).isTrue();
            assertThat(dto.getMessage()).contains("puede continuar");
        }

        @Test
        @DisplayName("downgrade a BASIC no elegible (saldo > 0): eligible=false y mensaje con el saldo (en pesos)")
        void downgradeToBasicNotEligible() {
            Plan standard = standardPlan();
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, standard, wallet(123_400L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.BASIC, null);

            assertThat(dto.isEligible()).isFalse();
            assertThat(dto.getMessage()).contains("1234");
        }

        @Test
        @DisplayName("fromPlan==null: mensaje de aplicación inmediata tras abono")
        void fromPlanNull_message() {
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, null, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.STANDARD, null);

            assertThat(dto.getMessage()).contains("se confirme el pago del abono,");
            assertThat(dto.getFromPlanCode()).isNull();
        }

        @Test
        @DisplayName("fromPlan==BASIC: mensaje de aplicación inmediata tras abono")
        void fromPlanBasic_message() {
            Plan basic = basicPlan();
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basic, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.STANDARD, null);

            assertThat(dto.getMessage()).contains("se confirme el pago del abono,");
        }

        @Test
        @DisplayName("upgrade entre STANDARD/PREMIUM sin monto indicado: requiredTopUp = mínimo del plan destino (en pesos)")
        void upgradeWithoutAmount_requiredTopUpIsTargetMinimum() {
            Plan standard = standardPlan();
            Plan premium = plan(3L, PlanCode.PREMIUM, null, 10_000_000_00L, null);
            CommercialDetails commercial = commercial(1L, standard, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.PREMIUM)).thenReturn(Optional.of(premium));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.PREMIUM, null);

            assertThat(dto.getRequiredTopUpAmountPesos()).isEqualTo(premium.getMinInvestmentCents() / 100);
            assertThat(dto.getMessage()).contains("se confirme el pago del abono,");
        }

        @Test
        @DisplayName("monto a invertir fuera del rango del plan destino: lanza ValidationException")
        void investmentOutOfRange_throwsValidationException() {
            Plan basic = basicPlan();
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basic, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            assertThatThrownBy(() -> service.previewPlanChange(1L, PlanCode.STANDARD, 1L))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("destino BASIC: solo targetMonthlyPriceCents poblado, min/max en null")
        void targetBasic_onlyMonthlyPricePopulated() {
            Plan standard = standardPlan();
            Plan basic = basicPlan();
            CommercialDetails commercial = commercial(1L, standard, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.BASIC)).thenReturn(Optional.of(basic));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.BASIC, null);

            assertThat(dto.getTargetMonthlyPricePesos()).isEqualTo(basic.getMonthlyPriceCents() / 100);
            assertThat(dto.getTargetMinInvestmentPesos()).isNull();
            assertThat(dto.getTargetMaxInvestmentPesos()).isNull();
        }

        @Test
        @DisplayName("destino no-BASIC: solo targetMinInvestmentPesos/targetMaxInvestmentPesos poblados")
        void targetNonBasic_onlyMinMaxPopulated() {
            Plan basic = basicPlan();
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basic, wallet(0L));
            when(commercialDetailsRepository.findById(1L)).thenReturn(Optional.of(commercial));
            when(planRepository.findByCodeAndActiveTrue(PlanCode.STANDARD)).thenReturn(Optional.of(standard));

            PlanChangePreviewResponseDTO dto = service.previewPlanChange(1L, PlanCode.STANDARD, null);

            assertThat(dto.getTargetMonthlyPricePesos()).isNull();
            assertThat(dto.getTargetMinInvestmentPesos()).isEqualTo(standard.getMinInvestmentCents() / 100);
            assertThat(dto.getTargetMaxInvestmentPesos()).isEqualTo(standard.getMaxInvestmentCents() / 100);
        }
    }

    // ─── cancelPlanChangeRequest ────────────────────────────────────────────

    @Nested
    @DisplayName("cancelPlanChangeRequest")
    class CancelPlanChangeRequest {

        @Test
        @DisplayName("solicitud inexistente: lanza EntityNotFoundException")
        void requestNotFound_throwsEntityNotFoundException() {
            when(planChangeRequestRepository.findById(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.cancelPlanChangeRequest(9L, 1L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("solicitud de otro comercial: lanza EntityNotFoundException con el mismo mensaje que 'no existe' "
                + "(hallazgo de seguridad: no debería revelar que la solicitud existe pero pertenece a otro comercial — no se corrige aquí)")
        void requestBelongsToAnotherCommercial_throwsEntityNotFoundException() {
            CommercialDetails owner = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(owner);
            request.setStatus(PlanChangeRequestStatus.REQUESTED);
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelPlanChangeRequest(999L, 5L))
                    .isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("estado distinto de REQUESTED/CONTRACT_PENDING_REVIEW: lanza ValidationException")
        void wrongStatus_throwsValidationException() {
            CommercialDetails owner = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(owner);
            request.setStatus(PlanChangeRequestStatus.PAYMENT_PENDING);
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));

            assertThatThrownBy(() -> service.cancelPlanChangeRequest(1L, 5L))
                    .isInstanceOf(ValidationException.class);

            verify(planChangeRequestRepository, never()).save(any());
        }

        @Test
        @DisplayName("caso feliz desde REQUESTED: marca CANCELLED y guarda")
        void happyPathFromRequested_marksCancelled() {
            CommercialDetails owner = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(owner);
            request.setStatus(PlanChangeRequestStatus.REQUESTED);
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanChangeRequest result = service.cancelPlanChangeRequest(1L, 5L);

            assertThat(result.getStatus()).isEqualTo(PlanChangeRequestStatus.CANCELLED);
            verify(planChangeRequestRepository).save(request);
        }

        @Test
        @DisplayName("caso feliz desde CONTRACT_PENDING_REVIEW: marca CANCELLED y guarda")
        void happyPathFromContractPendingReview_marksCancelled() {
            CommercialDetails owner = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(owner);
            request.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PlanChangeRequest result = service.cancelPlanChangeRequest(1L, 5L);

            assertThat(result.getStatus()).isEqualTo(PlanChangeRequestStatus.CANCELLED);
            verify(planChangeRequestRepository).save(request);
        }
    }

    // ─── getCurrent ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getCurrent")
    class GetCurrent {

        @Test
        @DisplayName("delega en findByCommercial_IdAndStatusNotIn y retorna el primero")
        void delegatesAndReturnsFirst() {
            PlanChangeRequest request = new PlanChangeRequest();
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any()))
                    .thenReturn(List.of(request));

            PlanChangeRequest result = service.getCurrent(1L);

            assertThat(result).isSameAs(request);
        }

        @Test
        @DisplayName("sin ninguna no-terminal: retorna null")
        void noneNonTerminal_returnsNull() {
            when(planChangeRequestRepository.findByCommercial_IdAndStatusNotIn(eq(1L), any())).thenReturn(List.of());

            PlanChangeRequest result = service.getCurrent(1L);

            assertThat(result).isNull();
        }
    }

    // ─── listPendingReview ──────────────────────────────────────────────────

    @Test
    @DisplayName("listPendingReview: delega en findByStatusNotIn")
    void listPendingReview_delegates() {
        PlanChangeRequest request = new PlanChangeRequest();
        when(planChangeRequestRepository.findByStatusNotIn(any())).thenReturn(List.of(request));

        List<PlanChangeRequest> result = service.listPendingReview();

        assertThat(result).containsExactly(request);
    }

    // ─── onContractSigned ───────────────────────────────────────────────────

    @Nested
    @DisplayName("onContractSigned")
    class OnContractSigned {

        @Test
        @DisplayName("purpose != PLAN_CHANGE: ignora el evento, no toca el repo de plan change requests")
        void wrongPurpose_ignoresEvent() {
            ContractSignedEvent event = new ContractSignedEvent(this, 1L, ContractPurpose.RECHARGE);

            service.onContractSigned(event);

            verifyNoInteractions(planChangeRequestRepository);
        }

        @Test
        @DisplayName("purpose == PLAN_CHANGE y requiredTopUp == null: aplica de inmediato")
        void planChangeWithNullTopUp_appliesImmediately() {
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(commercial);
            request.setToPlan(standard);
            request.setRequiredTopUpAmountCents(null);
            request.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            when(planChangeRequestRepository.findByContract_Id(1L)).thenReturn(Optional.of(request));
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.onContractSigned(new ContractSignedEvent(this, 1L, ContractPurpose.PLAN_CHANGE));

            assertThat(request.getStatus()).isEqualTo(PlanChangeRequestStatus.APPLIED);
            assertThat(commercial.getCurrentPlan()).isEqualTo(standard);
            verify(commercialDetailsRepository).save(commercial);
            verify(notificationService).createInternalNotification(eq(commercial.getUser().getId()), any(), any(), any());
        }

        @Test
        @DisplayName("purpose == PLAN_CHANGE y requiredTopUp <= 0: aplica de inmediato")
        void planChangeWithZeroTopUp_appliesImmediately() {
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(commercial);
            request.setToPlan(standard);
            request.setRequiredTopUpAmountCents(0L);
            request.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            when(planChangeRequestRepository.findByContract_Id(1L)).thenReturn(Optional.of(request));
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.onContractSigned(new ContractSignedEvent(this, 1L, ContractPurpose.PLAN_CHANGE));

            assertThat(request.getStatus()).isEqualTo(PlanChangeRequestStatus.APPLIED);
            assertThat(commercial.getCurrentPlan()).isEqualTo(standard);
        }

        @Test
        @DisplayName("purpose == PLAN_CHANGE y requiredTopUp > 0: pasa a PAYMENT_PENDING sin aplicar")
        void planChangeWithPositiveTopUp_movesToPaymentPending() {
            Plan basic = basicPlan();
            Plan premium = plan(3L, PlanCode.PREMIUM, null, 10_000_000_00L, null);
            CommercialDetails commercial = commercial(1L, basic, wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(commercial);
            request.setToPlan(premium);
            request.setRequiredTopUpAmountCents(500_000_00L);
            request.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            when(planChangeRequestRepository.findByContract_Id(1L)).thenReturn(Optional.of(request));
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.onContractSigned(new ContractSignedEvent(this, 1L, ContractPurpose.PLAN_CHANGE));

            assertThat(request.getStatus()).isEqualTo(PlanChangeRequestStatus.PAYMENT_PENDING);
            assertThat(commercial.getCurrentPlan()).isEqualTo(basic);
            verifyNoInteractions(commercialDetailsRepository, notificationService);
        }
    }

    // ─── applyIfPending ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("applyIfPending")
    class ApplyIfPending {

        @Test
        @DisplayName("solicitud inexistente: no-op, no llama nada más")
        void requestNotFound_isNoOp() {
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.empty());

            service.applyIfPending(5L);

            verify(planChangeRequestRepository, never()).save(any());
            verifyNoInteractions(commercialDetailsRepository, notificationService);
        }

        @Test
        @DisplayName("status != PAYMENT_PENDING: no-op, no llama nada más")
        void wrongStatus_isNoOp() {
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));

            service.applyIfPending(5L);

            verify(planChangeRequestRepository, never()).save(any());
            verifyNoInteractions(commercialDetailsRepository, notificationService);
        }

        @Test
        @DisplayName("status == PAYMENT_PENDING: aplica correctamente")
        void paymentPending_applies() {
            Plan standard = standardPlan();
            CommercialDetails commercial = commercial(1L, basicPlan(), wallet(0L));
            PlanChangeRequest request = new PlanChangeRequest();
            request.setId(5L);
            request.setCommercial(commercial);
            request.setToPlan(standard);
            request.setStatus(PlanChangeRequestStatus.PAYMENT_PENDING);
            when(planChangeRequestRepository.findById(5L)).thenReturn(Optional.of(request));
            when(planChangeRequestRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.applyIfPending(5L);

            assertThat(request.getStatus()).isEqualTo(PlanChangeRequestStatus.APPLIED);
            assertThat(commercial.getCurrentPlan()).isEqualTo(standard);
            verify(commercialDetailsRepository).save(commercial);
        }
    }
}
