package com.verygana2.controllers.finance.plans;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.finance.plans.requests.PlanPaymentRequestDTO;
import com.verygana2.dtos.finance.plans.responses.EffectivePlanStateResponseDTO;
import com.verygana2.dtos.finance.plans.responses.PlanPaymentStatusResponseDTO;
import com.verygana2.dtos.wompi.WompiCheckoutResponseDTO;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PlanService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PlanController}: cada endpoint resuelve el
 * {@link CommercialDetails} desde el userId del JWT y delega en
 * {@link PlanService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PlanController")
class PlanControllerTest {

    @Mock private PlanService planService;
    @Mock private CommercialDetailsService commercialDetailsService;

    private PlanController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanController(planService, commercialDetailsService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("initiatePayment: resuelve el commercial desde el JWT y delega en el service")
    void initiatePayment_delegates() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(9L);
        PlanPaymentRequestDTO request = new PlanPaymentRequestDTO();
        request.setPlanCode(PlanCode.STANDARD);
        request.setAmountCents(3_000_000L);
        WompiCheckoutResponseDTO expected = WompiCheckoutResponseDTO.builder().checkoutUrl("https://checkout").build();

        when(commercialDetailsService.getCommercialById(9L)).thenReturn(commercial);
        when(planService.initiatePlanPayment(commercial, PlanCode.STANDARD, 3_000_000L)).thenReturn(expected);

        var response = controller.initiatePayment(jwtWithUserId(9L), request);

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getPaymentStatus: pasa la referencia del path y el commercial resuelto del JWT")
    void getPaymentStatus_delegates() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(9L);
        PlanPaymentStatusResponseDTO expected = PlanPaymentStatusResponseDTO.builder().reference("REF-1").build();

        when(commercialDetailsService.getCommercialById(9L)).thenReturn(commercial);
        when(planService.getPaymentStatus("REF-1", commercial)).thenReturn(expected);

        var response = controller.getPaymentStatus(jwtWithUserId(9L), "REF-1");

        assertThat(response.getBody()).isSameAs(expected);
    }

    @Test
    @DisplayName("getEffectivePlanState: delega en el service con el commercial resuelto del JWT")
    void getEffectivePlanState_delegates() {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(9L);
        EffectivePlanStateResponseDTO expected = EffectivePlanStateResponseDTO.builder()
                .effectivePlan("STANDARD").hasActivePlan(true).build();

        when(commercialDetailsService.getCommercialById(9L)).thenReturn(commercial);
        when(planService.getEffectivePlanState(commercial)).thenReturn(expected);

        var response = controller.getEffectivePlanState(jwtWithUserId(9L));

        assertThat(response.getBody()).isSameAs(expected);
    }
}
