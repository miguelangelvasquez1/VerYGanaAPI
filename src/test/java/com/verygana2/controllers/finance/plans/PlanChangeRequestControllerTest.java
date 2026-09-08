package com.verygana2.controllers.finance.plans;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.finance.plans.requests.PlanChangeRequestDTO;
import com.verygana2.dtos.finance.plans.responses.PlanChangePreviewResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanChangeRequestResponseDTO;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PlanChangeRequestService;
import com.verygana2.services.interfaces.finance.PlanService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanChangeRequestController}: cada endpoint resuelve el
 * commercialId desde el JWT y delega en el service correspondiente. Para
 * aprobar el contrato delega en {@link CommercialContractService}, no en
 * {@link PlanChangeRequestService}; para el top-up delega en {@link PlanService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanChangeRequestController")
class PlanChangeRequestControllerTest {

    @Mock private PlanChangeRequestService planChangeRequestService;
    @Mock private CommercialContractService contractService;
    @Mock private PlanService planService;
    @Mock private CommercialDetailsService commercialDetailsService;

    private PlanChangeRequestController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanChangeRequestController(
                planChangeRequestService, contractService, planService, commercialDetailsService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    private Plan planWithCode(Long id, PlanCode code) {
        return Plan.builder().id(id).code(code).build();
    }

    @Nested
    @DisplayName("GET /preview")
    class PreviewPlanChange {

        @Test
        @DisplayName("delega en el service con el commercialId del JWT y los query params")
        void previewPlanChange_delegates() {
            PlanChangePreviewResponseDTO expected = new PlanChangePreviewResponseDTO();
            expected.setEligible(true);
            when(planChangeRequestService.previewPlanChange(9L, PlanCode.PREMIUM, 500_000_000L))
                    .thenReturn(expected);

            var response = controller.previewPlanChange(jwtWithUserId(9L), PlanCode.PREMIUM, 500_000_000L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("acepta intendedInvestmentAmountCents nulo (no requerido)")
        void previewPlanChange_nullInvestmentAmount() {
            PlanChangePreviewResponseDTO expected = new PlanChangePreviewResponseDTO();
            when(planChangeRequestService.previewPlanChange(9L, PlanCode.BASIC, null))
                    .thenReturn(expected);

            var response = controller.previewPlanChange(jwtWithUserId(9L), PlanCode.BASIC, null);

            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST / (requestPlanChange)")
    class RequestPlanChange {

        @Test
        @DisplayName("delega en el service y mapea la entidad devuelta a DTO")
        void requestPlanChange_delegatesAndMaps() {
            PlanChangeRequestDTO body = new PlanChangeRequestDTO();
            body.setTargetPlanCode(PlanCode.PREMIUM);
            body.setIntendedInvestmentAmountCents(500_000_000L);

            Plan fromPlan = planWithCode(1L, PlanCode.STANDARD);
            Plan toPlan = planWithCode(2L, PlanCode.PREMIUM);
            CommercialContract contract = new CommercialContract();
            contract.setId(77L);
            contract.setStatus(ContractStatus.PENDING_BUSINESS_REVIEW);

            PlanChangeRequest created = new PlanChangeRequest();
            created.setId(5L);
            created.setFromPlan(fromPlan);
            created.setToPlan(toPlan);
            created.setRequiredTopUpAmountCents(100L);
            created.setStatus(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            created.setContract(contract);

            when(planChangeRequestService.requestPlanChange(9L, PlanCode.PREMIUM, 500_000_000L))
                    .thenReturn(created);

            ContractSummaryResponseDTO contractSummary = new ContractSummaryResponseDTO();
            contractSummary.setDownloadUrl("https://r2/otrosi-77.pdf");
            when(contractService.getForCommercial(77L, 9L)).thenReturn(contractSummary);

            var response = controller.requestPlanChange(jwtWithUserId(9L), body);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            PlanChangeRequestResponseDTO dto = response.getBody();
            assertThat(dto).isNotNull();
            assertThat(dto.getId()).isEqualTo(5L);
            assertThat(dto.getFromPlanCode()).isEqualTo(PlanCode.STANDARD);
            assertThat(dto.getToPlanCode()).isEqualTo(PlanCode.PREMIUM);
            assertThat(dto.getRequiredTopUpAmountCents()).isEqualTo(100L);
            assertThat(dto.getStatus()).isEqualTo(PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW);
            assertThat(dto.getContractId()).isEqualTo(77L);
            assertThat(dto.getContractStatus()).isEqualTo(ContractStatus.PENDING_BUSINESS_REVIEW);
            assertThat(dto.getContractDownloadUrl()).isEqualTo("https://r2/otrosi-77.pdf");
        }

        @Test
        @DisplayName("no explota cuando fromPlan y contract son null")
        void requestPlanChange_handlesNullFromPlanAndContract() {
            PlanChangeRequestDTO body = new PlanChangeRequestDTO();
            body.setTargetPlanCode(PlanCode.BASIC);

            Plan toPlan = planWithCode(3L, PlanCode.BASIC);
            PlanChangeRequest created = new PlanChangeRequest();
            created.setId(6L);
            created.setFromPlan(null);
            created.setToPlan(toPlan);
            created.setStatus(PlanChangeRequestStatus.REQUESTED);
            created.setContract(null);

            when(planChangeRequestService.requestPlanChange(9L, PlanCode.BASIC, null))
                    .thenReturn(created);

            var response = controller.requestPlanChange(jwtWithUserId(9L), body);

            PlanChangeRequestResponseDTO dto = response.getBody();
            assertThat(dto).isNotNull();
            assertThat(dto.getFromPlanCode()).isNull();
            assertThat(dto.getContractId()).isNull();
            assertThat(dto.getContractStatus()).isNull();
            assertThat(dto.getToPlanCode()).isEqualTo(PlanCode.BASIC);
        }
    }

    @Nested
    @DisplayName("GET /current")
    class GetCurrent {

        @Test
        @DisplayName("mapea la solicitud actual cuando existe")
        void getCurrent_returnsMapped() {
            Plan toPlan = planWithCode(2L, PlanCode.PREMIUM);
            PlanChangeRequest current = new PlanChangeRequest();
            current.setId(8L);
            current.setToPlan(toPlan);
            current.setStatus(PlanChangeRequestStatus.PAYMENT_PENDING);

            when(planChangeRequestService.getCurrent(9L)).thenReturn(current);

            var response = controller.getCurrent(jwtWithUserId(9L));

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getId()).isEqualTo(8L);
        }

        @Test
        @DisplayName("devuelve body null cuando no hay solicitud actual")
        void getCurrent_returnsNullBodyWhenNoCurrent() {
            when(planChangeRequestService.getCurrent(9L)).thenReturn(null);

            var response = controller.getCurrent(jwtWithUserId(9L));

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNull();
        }
    }

    @Nested
    @DisplayName("POST /{id}/cancel")
    class Cancel {

        @Test
        @DisplayName("delega en el service con commercialId e id del path")
        void cancel_delegates() {
            Plan toPlan = planWithCode(2L, PlanCode.STANDARD);
            PlanChangeRequest cancelled = new PlanChangeRequest();
            cancelled.setId(11L);
            cancelled.setToPlan(toPlan);
            cancelled.setStatus(PlanChangeRequestStatus.CANCELLED);

            when(planChangeRequestService.cancelPlanChangeRequest(9L, 11L)).thenReturn(cancelled);

            var response = controller.cancel(jwtWithUserId(9L), 11L);

            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().getStatus()).isEqualTo(PlanChangeRequestStatus.CANCELLED);
        }
    }

    @Nested
    @DisplayName("POST /contract/{contractId}/approve")
    class ApproveContract {

        @Test
        @DisplayName("delega en CommercialContractService.businessApproveContract, no en PlanChangeRequestService")
        void approveContract_delegatesToContractService() {
            ContractSummaryResponseDTO expected = new ContractSummaryResponseDTO();
            expected.setContractId(77L);
            when(contractService.businessApproveContract(77L, 9L)).thenReturn(expected);

            var response = controller.approveContract(jwtWithUserId(9L), 77L);

            assertThat(response.getBody()).isSameAs(expected);
            verify(contractService).businessApproveContract(77L, 9L);
            verify(planChangeRequestService, never()).requestPlanChange(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("POST /{id}/top-up-checkout")
    class GenerateTopUpCheckout {

        @Test
        @DisplayName("resuelve el commercial desde el JWT y delega en PlanService")
        void generateTopUpCheckout_delegates() {
            CommercialDetails commercial = new CommercialDetails();
            commercial.setId(9L);
            WompiCheckoutResponseDTO expected = WompiCheckoutResponseDTO.builder()
                    .checkoutUrl("https://checkout").build();

            when(commercialDetailsService.getCommercialById(9L)).thenReturn(commercial);
            when(planService.generatePlanChangeTopUpCheckout(11L, commercial)).thenReturn(expected);

            var response = controller.generateTopUpCheckout(jwtWithUserId(9L), 11L);

            assertThat(response.getBody()).isSameAs(expected);
            verify(commercialDetailsService).getCommercialById(9L);
        }
    }
}
