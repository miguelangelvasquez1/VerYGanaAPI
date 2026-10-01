package com.verygana2.services.finance;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.services.wompi.WompiPayoutClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PayoutExecutionServiceImpl}: la ejecución aislada (una
 * transacción propia por payout, ver {@link com.verygana2.services.interfaces.finance.PayoutExecutionService})
 * de la transferencia Wompi de un único Payout. Wompi está mockeado.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PayoutExecutionServiceImpl")
class PayoutExecutionServiceImplTest {

    @Mock private PayoutRepository payoutRepository;
    @Mock private PayoutMethodRepository payoutMethodRepository;
    @Mock private WompiPayoutClient wompiPayoutClient;
    @Mock private WompiTransactionRepository wompiTransactionRepository;
    @Mock private WompiPayoutConfig wompiPayoutConfig;

    private PayoutExecutionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PayoutExecutionServiceImpl(payoutRepository, payoutMethodRepository,
                wompiPayoutClient, wompiTransactionRepository, wompiPayoutConfig);
    }

    private CommercialDetails commercial(Long id, String name) {
        CommercialDetails c = new CommercialDetails();
        c.setId(id);
        c.setCompanyName(name);
        User user = new User();
        user.setEmail("comercial" + id + "@verygana.co");
        c.setUser(user);
        return c;
    }

    private PayoutMethod verifiedMethod() {
        return PayoutMethod.builder().verificationStatus(VerificationStatus.VERIFIED)
                .type(PayoutMethod.PayoutMethodType.BANK_ACCOUNT)
                .accountHolderDocType(PayoutMethod.DocType.CC).accountHolderDoc("123")
                .accountHolderName("Juan").bankCode("bank-uuid-1007")
                .bankAccountType(PayoutMethod.BankAccountType.SAVINGS).accountNumber("999").build();
    }

    @Nested
    @DisplayName("executeScheduledPayout")
    class ExecuteScheduledPayout {

        @Test
        @DisplayName("transferencia exitosa: el payout pasa a PROCESSING y queda vinculado a la WompiTransaction")
        void successfulTransfer_movesToProcessing() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));
            when(wompiPayoutConfig.getAccountId()).thenReturn("acc_123");

            WompiPayoutResponseDTO response = new WompiPayoutResponseDTO();
            response.setStatus(201);
            response.setCode("OK");
            WompiPayoutResponseDTO.PayoutData data = new WompiPayoutResponseDTO.PayoutData();
            data.setPayoutId("wp_123");
            data.setSuccess(1);
            data.setFailed(0);
            response.setData(data);
            when(wompiPayoutClient.createPayout(any())).thenReturn(response);
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.executeScheduledPayout(payout.getId());

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.PROCESSING);
            assertThat(payout.getWompiTransaction()).isNotNull();
            assertThat(payout.getWompiTransaction().getStatus()).isEqualTo(WompiTransactionStatus.PENDING);
        }

        @Test
        @DisplayName("sin método de pago verificado: el payout queda FAILED con el motivo")
        void noVerifiedMethod_marksFailed() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.empty());

            service.executeScheduledPayout(payout.getId());

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isNotBlank();
        }

        @Test
        @DisplayName("Wompi rechaza el payout (status != aceptado): el payout queda FAILED con el status de Wompi")
        void wompiRejectsPayout_marksFailed() {
            CommercialDetails commercial = commercial(1L, "Tienda X");
            Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                    .netAmountCents(90_000L).status(PayoutStatus.SCHEDULED).build();

            when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
            when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                    .thenReturn(Optional.of(verifiedMethod()));
            when(wompiPayoutConfig.getAccountId()).thenReturn("acc_123");

            WompiPayoutResponseDTO response = new WompiPayoutResponseDTO();
            response.setStatus(400);
            response.setCode("VALIDATION_ERROR");
            response.setMessage("Cuenta destino inválida");
            when(wompiPayoutClient.createPayout(any())).thenReturn(response);
            when(wompiTransactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            service.executeScheduledPayout(payout.getId());

            assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED);
            assertThat(payout.getFailureReason()).isEqualTo("VALIDATION_ERROR: Cuenta destino inválida");
        }
    }

    @Test
    @DisplayName("executeRetry: incrementa retryCount, reintenta y limpia el motivo de fallo previo")
    void executeRetry_incrementsAndRetries() {
        CommercialDetails commercial = commercial(1L, "Tienda X");
        Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(90_000L).status(PayoutStatus.FAILED).retryCount(0)
                .failureReason("Error previo").build();

        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(wompiPayoutConfig.getPayout()).thenReturn(new WompiPayoutConfig.Payout());
        when(payoutMethodRepository.findFirstByCommercialIdAndVerificationStatusAndActiveTrue(1L, VerificationStatus.VERIFIED))
                .thenReturn(Optional.empty()); // vuelve a fallar por falta de método verificado

        service.executeRetry(payout.getId());

        assertThat(payout.getRetryCount()).isEqualTo(1);
        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.FAILED); // vuelve a fallar, pero se intentó
    }

    @Test
    @DisplayName("executeRetry: al alcanzar el máximo de reintentos, marca EXHAUSTED sin volver a llamar a Wompi")
    void executeRetry_reachesMaxRetries_marksExhaustedWithoutCallingWompi() {
        CommercialDetails commercial = commercial(1L, "Tienda X");
        WompiPayoutConfig.Payout payoutConfig = new WompiPayoutConfig.Payout();
        payoutConfig.setMaxRetries(3);
        Payout payout = Payout.builder().id(UUID.randomUUID()).commercial(commercial)
                .netAmountCents(90_000L).status(PayoutStatus.FAILED).retryCount(3)
                .failureReason("Cuenta destino inválida").build();

        when(payoutRepository.findById(payout.getId())).thenReturn(Optional.of(payout));
        when(wompiPayoutConfig.getPayout()).thenReturn(payoutConfig);

        service.executeRetry(payout.getId());

        assertThat(payout.getStatus()).isEqualTo(PayoutStatus.EXHAUSTED);
        assertThat(payout.getRetryCount()).isEqualTo(3); // no se incrementa: no hubo un intento nuevo
        verify(wompiPayoutClient, never()).createPayout(any());
        verify(payoutMethodRepository, never())
                .findFirstByCommercialIdAndVerificationStatusAndActiveTrue(any(), any());
    }
}
