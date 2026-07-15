package com.verygana2.services.finance;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.exceptions.compliance.ScreeningHitException;
import com.verygana2.exceptions.payoutExceptions.InvalidPayoutMethodStateException;
import com.verygana2.exceptions.payoutExceptions.OtpVerificationException;
import com.verygana2.exceptions.payoutExceptions.PayoutMethodNotFoundException;
import com.verygana2.mappers.finance.PayoutMethodMapper;
import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutMethodServiceImpl}: alta de un método de pago (con
 * validación de campos por tipo), el flujo OTP para NEQUI/DAVIPLATA, y el
 * flujo de revisión manual para BANK_TRANSFER incluyendo el screening
 * antilavado que puede rechazar automáticamente al titular.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutMethodServiceImpl")
class PayoutMethodServiceImplTest {

    @Mock private CommercialDetailsService commercialDetailsService;
    @Mock private PayoutMethodRepository payoutMethodRepository;
    @Mock private PayoutMethodMapper payoutMethodMapper;
    @Mock private TwilioSmsService twilioSmsService;
    @Mock private ScreeningService screeningService;

    private PayoutMethodServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayoutMethodServiceImpl(commercialDetailsService, payoutMethodRepository, payoutMethodMapper,
                twilioSmsService, screeningService);
    }

    private CreatePayoutMethodRequestDTO nequiRequest() {
        CreatePayoutMethodRequestDTO request = new CreatePayoutMethodRequestDTO();
        request.setType(PayoutMethodType.NEQUI);
        request.setAlias("Nequi personal");
        request.setPhoneNumber("3001234567");
        request.setAccountHolderName("Juan Pérez");
        request.setAccountHolderDoc("123456");
        request.setAccountHolderDocType(PayoutMethod.DocType.CC);
        return request;
    }

    @Nested
    @DisplayName("createPayoutMethod")
    class CreatePayoutMethod {

        @Test
        @DisplayName("NEQUI válido: se registra y dispara el OTP automáticamente, queda AWAITING_OTP")
        void validNequi_sendsOtpAndAwaits() {
            PayoutMethod mapped = PayoutMethod.builder().phoneNumber("3001234567").build();
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(new CommercialDetails());
            when(payoutMethodMapper.toPayoutMethod(any())).thenReturn(mapped);
            when(payoutMethodRepository.save(any(PayoutMethod.class))).thenAnswer(inv -> {
                PayoutMethod pm = inv.getArgument(0);
                pm.setId(10L);
                return pm;
            });

            var response = service.createPayoutMethod(1L, nequiRequest());

            assertThat(response.getId()).isEqualTo(10L);
            verify(twilioSmsService).sendOtp("3001234567");
        }

        @Test
        @DisplayName("BANK_TRANSFER válido: queda directo en UNDER_REVIEW, sin disparar OTP")
        void validBankTransfer_goesUnderReviewWithoutOtp() {
            CreatePayoutMethodRequestDTO request = new CreatePayoutMethodRequestDTO();
            request.setType(PayoutMethodType.BANK_TRANSFER);
            request.setAlias("Cuenta Bancolombia");
            request.setBankCode("1007");
            request.setAccountNumber("123456789");
            request.setBankAccountType(PayoutMethod.BankAccountType.SAVINGS);
            request.setAccountHolderName("Empresa SAS");
            request.setAccountHolderDoc("900123456");
            request.setAccountHolderDocType(PayoutMethod.DocType.NIT);

            PayoutMethod mapped = PayoutMethod.builder().build();
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(new CommercialDetails());
            when(payoutMethodMapper.toPayoutMethod(any())).thenReturn(mapped);
            when(payoutMethodRepository.save(any(PayoutMethod.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createPayoutMethod(1L, request);

            assertThat(mapped.getVerificationStatus()).isEqualTo(VerificationStatus.UNDER_REVIEW);
            verify(twilioSmsService, never()).sendOtp(anyString());
        }

        @Test
        @DisplayName("NEQUI sin número de teléfono: lanza IllegalArgumentException")
        void nequiWithoutPhone_throwsIllegalArgumentException() {
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(new CommercialDetails());
            CreatePayoutMethodRequestDTO request = nequiRequest();
            request.setPhoneNumber(null);

            assertThatThrownBy(() -> service.createPayoutMethod(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("NEQUI con teléfono con formato inválido: lanza IllegalArgumentException")
        void nequiWithInvalidPhoneFormat_throwsIllegalArgumentException() {
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(new CommercialDetails());
            CreatePayoutMethodRequestDTO request = nequiRequest();
            request.setPhoneNumber("12345"); // no cumple el patrón 3XXXXXXXXX

            assertThatThrownBy(() -> service.createPayoutMethod(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("BANK_TRANSFER sin bankCode: lanza IllegalArgumentException")
        void bankTransferWithoutBankCode_throwsIllegalArgumentException() {
            when(commercialDetailsService.getCommercialById(1L)).thenReturn(new CommercialDetails());
            CreatePayoutMethodRequestDTO request = new CreatePayoutMethodRequestDTO();
            request.setType(PayoutMethodType.BANK_TRANSFER);
            request.setAccountNumber("123");
            request.setBankAccountType(PayoutMethod.BankAccountType.SAVINGS);

            assertThatThrownBy(() -> service.createPayoutMethod(1L, request))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("verifyOtp")
    class VerifyOtp {

        @Test
        @DisplayName("código correcto en estado AWAITING_OTP: verifica el método")
        void correctCodeWhileAwaiting_marksVerified() {
            PayoutMethod method = PayoutMethod.builder()
                    .verificationStatus(VerificationStatus.AWAITING_OTP).phoneNumber("3001234567").build();
            when(payoutMethodRepository.findByIdAndCommercialId(10L, 1L)).thenReturn(Optional.of(method));
            when(twilioSmsService.verifyOtp("3001234567", "123456")).thenReturn(true);

            service.verifyOtp(1L, 10L, "123456");

            assertThat(method.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("código incorrecto: lanza OtpVerificationException")
        void incorrectCode_throwsOtpVerificationException() {
            PayoutMethod method = PayoutMethod.builder()
                    .verificationStatus(VerificationStatus.AWAITING_OTP).phoneNumber("3001234567").build();
            when(payoutMethodRepository.findByIdAndCommercialId(10L, 1L)).thenReturn(Optional.of(method));
            when(twilioSmsService.verifyOtp("3001234567", "000000")).thenReturn(false);

            assertThatThrownBy(() -> service.verifyOtp(1L, 10L, "000000"))
                    .isInstanceOf(OtpVerificationException.class);
        }

        @Test
        @DisplayName("método que no está esperando OTP: lanza InvalidPayoutMethodStateException")
        void notAwaitingOtp_throwsInvalidPayoutMethodStateException() {
            PayoutMethod method = PayoutMethod.builder().verificationStatus(VerificationStatus.VERIFIED).build();
            when(payoutMethodRepository.findByIdAndCommercialId(10L, 1L)).thenReturn(Optional.of(method));

            assertThatThrownBy(() -> service.verifyOtp(1L, 10L, "123456"))
                    .isInstanceOf(InvalidPayoutMethodStateException.class);
        }

        @Test
        @DisplayName("método de otro comercial o inexistente: lanza PayoutMethodNotFoundException")
        void notOwnedOrMissing_throwsPayoutMethodNotFoundException() {
            when(payoutMethodRepository.findByIdAndCommercialId(10L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.verifyOtp(1L, 10L, "123456"))
                    .isInstanceOf(PayoutMethodNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("adminVerifyMethod")
    class AdminVerifyMethod {

        @Test
        @DisplayName("UNDER_REVIEW sin coincidencias en listas restrictivas: verifica el método")
        void underReviewWithoutScreeningHit_marksVerified() {
            CommercialDetails commercial = new CommercialDetails();
            com.verygana2.models.User user = new com.verygana2.models.User();
            user.setId(5L);
            commercial.setUser(user);
            PayoutMethod method = PayoutMethod.builder()
                    .verificationStatus(VerificationStatus.UNDER_REVIEW)
                    .commercial(commercial).accountHolderName("Juan").accountHolderDoc("123").build();

            when(payoutMethodRepository.findById(10L)).thenReturn(Optional.of(method));

            service.adminVerifyMethod(10L);

            assertThat(method.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        }

        @Test
        @DisplayName("titular en lista restrictiva: rechaza automáticamente el método y lanza IllegalStateException")
        void screeningHit_autoRejectsAndThrows() {
            CommercialDetails commercial = new CommercialDetails();
            com.verygana2.models.User user = new com.verygana2.models.User();
            user.setId(5L);
            commercial.setUser(user);
            PayoutMethod method = PayoutMethod.builder()
                    .verificationStatus(VerificationStatus.UNDER_REVIEW)
                    .commercial(commercial).accountHolderName("Juan").accountHolderDoc("123").build();

            when(payoutMethodRepository.findById(10L)).thenReturn(Optional.of(method));
            doThrow(new ScreeningHitException(ScreeningList.OFAC_SDN, ScreeningStatus.HIT, "Juan"))
                    .when(screeningService).screenOrThrow(any(), anyString(), anyString());

            assertThatThrownBy(() -> service.adminVerifyMethod(10L)).isInstanceOf(IllegalStateException.class);
            assertThat(method.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        }

        @Test
        @DisplayName("método que no está UNDER_REVIEW: lanza InvalidPayoutMethodStateException")
        void notUnderReview_throwsInvalidPayoutMethodStateException() {
            PayoutMethod method = PayoutMethod.builder().verificationStatus(VerificationStatus.VERIFIED).build();
            when(payoutMethodRepository.findById(10L)).thenReturn(Optional.of(method));

            assertThatThrownBy(() -> service.adminVerifyMethod(10L))
                    .isInstanceOf(InvalidPayoutMethodStateException.class);
        }
    }

    @Test
    @DisplayName("adminRejectMethod: rechaza y guarda el motivo, sin importar el estado previo")
    void adminRejectMethod_setsRejectedWithReason() {
        PayoutMethod method = PayoutMethod.builder().verificationStatus(VerificationStatus.UNDER_REVIEW).build();
        when(payoutMethodRepository.findById(10L)).thenReturn(Optional.of(method));

        service.adminRejectMethod(10L, "Datos inconsistentes");

        assertThat(method.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(method.getRejectionReason()).isEqualTo("Datos inconsistentes");
    }

    @Test
    @DisplayName("deactivatePayoutMethod: lo desactiva sin borrarlo")
    void deactivatePayoutMethod_setsActiveFalse() {
        PayoutMethod method = PayoutMethod.builder().active(true).build();
        when(payoutMethodRepository.findByIdAndCommercialId(10L, 1L)).thenReturn(Optional.of(method));

        service.deactivatePayoutMethod(1L, 10L);

        assertThat(method.isActive()).isFalse();
    }
}
