package com.verygana2.controllers.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.RejectPayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutMethodAdminController} (clase anotada con
 * {@code @PreAuthorize("hasRole('ADMIN')")} — no verificado aquí, cubierto por
 * el test de integración correspondiente). Cada endpoint delega directamente
 * en {@link PayoutMethodService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutMethodAdminController")
class PayoutMethodAdminControllerTest {

    @Mock private PayoutMethodService payoutMethodService;

    private PayoutMethodAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new PayoutMethodAdminController(payoutMethodService);
    }

    @Nested
    @DisplayName("GET / (getByStatus)")
    class GetByStatus {

        @Test
        @DisplayName("delega en el service con el status y el Pageable armado a partir de page/size")
        void getByStatus_delegates() {
            @SuppressWarnings("unchecked")
            PagedResponse<PayoutMethodResponseDTO> expected = mock(PagedResponse.class);
            when(payoutMethodService.getByStatus(eq(VerificationStatus.UNDER_REVIEW), eq(PageRequest.of(0, 20))))
                    .thenReturn(expected);

            var response = controller.getByStatus(VerificationStatus.UNDER_REVIEW, 0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isSameAs(expected);
        }

        @Test
        @DisplayName("respeta page/size custom y otro status")
        void getByStatus_customPageAndStatus() {
            @SuppressWarnings("unchecked")
            PagedResponse<PayoutMethodResponseDTO> expected = mock(PagedResponse.class);
            when(payoutMethodService.getByStatus(eq(VerificationStatus.VERIFIED), eq(PageRequest.of(2, 5))))
                    .thenReturn(expected);

            var response = controller.getByStatus(VerificationStatus.VERIFIED, 2, 5);

            assertThat(response.getBody()).isSameAs(expected);

            ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
            verify(payoutMethodService).getByStatus(eq(VerificationStatus.VERIFIED), pageableCaptor.capture());
            assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("POST /{id}/verify")
    class Verify {

        @Test
        @DisplayName("delega en adminVerifyMethod con el id del path")
        void verify_delegates() {
            var response = controller.verify(42L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payoutMethodService).adminVerifyMethod(42L);
        }
    }

    @Nested
    @DisplayName("POST /{id}/reject")
    class Reject {

        @Test
        @DisplayName("delega en adminRejectMethod con el id del path y el motivo del body")
        void reject_delegates() {
            RejectPayoutMethodRequestDTO request = new RejectPayoutMethodRequestDTO();
            request.setReason("Documento ilegible");

            var response = controller.reject(42L, request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(payoutMethodService).adminRejectMethod(42L, "Documento ilegible");
        }
    }

    @Nested
    @DisplayName("GET /{id}/certificate")
    class GetCertificate {

        @Test
        @DisplayName("delega en streamCertificate con el id y el response del servlet")
        void getCertificate_delegates() throws Exception {
            HttpServletResponse servletResponse = mock(HttpServletResponse.class);

            controller.getCertificate(42L, servletResponse);

            verify(payoutMethodService).streamCertificate(42L, servletResponse);
        }
    }
}
