package com.verygana2.controllers.commercial;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import com.verygana2.dtos.FileUploadRequestDTO;
import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.ConfirmPayoutMethodCertificateUploadRequestDTO;
import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.requests.VerifyOtpRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutBankResponseDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.dtos.generic.AssetUploadPermissionDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.models.finance.PayoutMethod.DocType;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutMethodController}: cada endpoint resuelve el
 * commercialId desde el userId del JWT y delega en {@link PayoutMethodService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutMethodController")
class PayoutMethodControllerTest {

    @Mock private PayoutMethodService payoutMethodService;

    private PayoutMethodController controller;

    @BeforeEach
    void setUp() {
        controller = new PayoutMethodController(payoutMethodService);
    }

    private Jwt jwtWithUserId(Long userId) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("userId")).thenReturn(userId);
        return jwt;
    }

    @Nested
    @DisplayName("POST /")
    class Create {

        @Test
        @DisplayName("crea el método de pago para el commercial del JWT y responde 201")
        void create_delegates() {
            CreatePayoutMethodRequestDTO request = new CreatePayoutMethodRequestDTO();
            request.setType(PayoutMethodType.NEQUI);
            request.setAlias("Mi Nequi");
            request.setPhoneNumber("3001234567");
            request.setAccountHolderName("Juan Perez");
            request.setAccountHolderDocType(DocType.CC);
            request.setAccountHolderDoc("123456789");

            EntityCreatedResponseDTO expected = new EntityCreatedResponseDTO(5L, "Creado", Instant.now());
            when(payoutMethodService.createPayoutMethod(9L, request)).thenReturn(expected);

            ResponseEntity<EntityCreatedResponseDTO> response = controller.create(jwtWithUserId(9L), request);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("GET /banks")
    class GetBanks {

        @Test
        @DisplayName("devuelve el catálogo de bancos del service")
        void getBanks_delegates() {
            List<PayoutBankResponseDTO> expected = List.of(new PayoutBankResponseDTO("1", "Bancolombia"));
            when(payoutMethodService.getAvailableBanks()).thenReturn(expected);

            ResponseEntity<List<PayoutBankResponseDTO>> response = controller.getBanks();

            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST /{id}/verify-otp")
    class VerifyOtp {

        @Test
        @DisplayName("delega en el service con el commercial, el id y el código del OTP")
        void verifyOtp_delegates() {
            VerifyOtpRequestDTO request = new VerifyOtpRequestDTO();
            request.setCode("123456");

            ResponseEntity<Void> response = controller.verifyOtp(jwtWithUserId(9L), 5L, request);

            verify(payoutMethodService).verifyOtp(9L, 5L, "123456");
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("POST /{id}/resend-otp")
    class ResendOtp {

        @Test
        @DisplayName("delega en el service con el commercial y el id del método")
        void resendOtp_delegates() {
            ResponseEntity<Void> response = controller.resendOtp(jwtWithUserId(9L), 5L);

            verify(payoutMethodService).resendOtp(9L, 5L);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("GET /")
    class GetAll {

        @Test
        @DisplayName("delega en el service con el commercial y el pageable construido de page/size")
        void getAll_delegates() {
            PagedResponse<PayoutMethodResponseDTO> expected =
                    PagedResponse.<PayoutMethodResponseDTO>builder().data(List.of()).build();
            when(payoutMethodService.getByCommercialId(eq(9L), any(Pageable.class))).thenReturn(expected);

            ResponseEntity<PagedResponse<PayoutMethodResponseDTO>> response =
                    controller.getAll(jwtWithUserId(9L), 0, 10);

            verify(payoutMethodService).getByCommercialId(9L, PageRequest.of(0, 10));
            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("PUT /{id}/deactivate")
    class Deactivate {

        @Test
        @DisplayName("delega en el service con el commercial y el id del método")
        void deactivate_delegates() {
            ResponseEntity<Void> response = controller.deactivate(jwtWithUserId(9L), 5L);

            verify(payoutMethodService).deactivatePayoutMethod(9L, 5L);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("PUT /{id}/set-default")
    class SetDefault {

        @Test
        @DisplayName("delega en el service con el commercial y el id del método")
        void setDefault_delegates() {
            ResponseEntity<Void> response = controller.setDefault(jwtWithUserId(9L), 5L);

            verify(payoutMethodService).setDefaultPayoutMethod(9L, 5L);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("POST /{id}/certificate/prepare")
    class PrepareCertificateUpload {

        @Test
        @DisplayName("delega en el service con el commercial, el id del método y los metadatos del archivo")
        void prepareCertificateUpload_delegates() {
            FileUploadRequestDTO metadata = new FileUploadRequestDTO(
                    "cert.pdf", "application/pdf", 1024L, null, null);
            AssetUploadPermissionDTO expected = AssetUploadPermissionDTO.builder().assetId(3L).build();

            when(payoutMethodService.prepareCertificateUpload(9L, 5L, metadata)).thenReturn(expected);

            ResponseEntity<AssetUploadPermissionDTO> response =
                    controller.prepareCertificateUpload(jwtWithUserId(9L), 5L, metadata);

            assertThat(response.getBody()).isSameAs(expected);
        }
    }

    @Nested
    @DisplayName("POST /{id}/certificate/confirm")
    class ConfirmCertificateUpload {

        @Test
        @DisplayName("delega en el service con el commercial, el id del método y el id del asset")
        void confirmCertificateUpload_delegates() {
            ConfirmPayoutMethodCertificateUploadRequestDTO request =
                    mock(ConfirmPayoutMethodCertificateUploadRequestDTO.class);
            when(request.getCertificateAssetId()).thenReturn(3L);

            EntityCreatedResponseDTO expected = new EntityCreatedResponseDTO(5L, "Confirmado", Instant.now());
            when(payoutMethodService.confirmCertificateUpload(9L, 5L, 3L)).thenReturn(expected);

            ResponseEntity<EntityCreatedResponseDTO> response =
                    controller.confirmCertificateUpload(jwtWithUserId(9L), 5L, request);

            assertThat(response.getBody()).isSameAs(expected);
        }
    }
}
