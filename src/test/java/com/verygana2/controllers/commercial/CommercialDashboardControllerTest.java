package com.verygana2.controllers.commercial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO;
import com.verygana2.models.enums.commercial.DashboardPeriod;
import com.verygana2.services.interfaces.commercial.CommercialDashboardService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link CommercialDashboardController}: resuelve el commercialId desde
 * el JWT y delega en {@link CommercialDashboardService}, con LAST_30_DAYS por defecto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommercialDashboardController")
class CommercialDashboardControllerTest {

    @Mock private CommercialDashboardService dashboardService;

    private CommercialDashboardController controller;

    @BeforeEach
    void setUp() {
        controller = new CommercialDashboardController(dashboardService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Test
    @DisplayName("getSummary: pasa el commercialId del JWT y el periodo recibido")
    void getSummary_delegates() {
        CommercialDashboardSummaryResponseDTO expected =
                CommercialDashboardSummaryResponseDTO.builder().build();
        when(dashboardService.getSummary(7L, DashboardPeriod.THIS_MONTH)).thenReturn(expected);

        var response = controller.getSummary(jwtWithUserId(7L), DashboardPeriod.THIS_MONTH);

        assertThat(response.getBody()).isSameAs(expected);
        verify(dashboardService).getSummary(7L, DashboardPeriod.THIS_MONTH);
    }
}
