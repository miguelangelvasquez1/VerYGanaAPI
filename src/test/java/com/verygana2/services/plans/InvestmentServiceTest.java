package com.verygana2.services.plans;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.finance.responses.InvestmentResponseDTO;
import com.verygana2.models.User;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.WalletRepository;
import com.verygana2.repositories.details.CommercialDetailsRepository;
import com.verygana2.repositories.finance.plans.InvestmentRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;

import jakarta.validation.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link InvestmentService}: registro de depósitos publicitarios
 * (creación de wallet on-the-fly, monto mínimo, conversión COP→centavos) y
 * las notificaciones de agotamiento/reposición de presupuesto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvestmentService")
class InvestmentServiceTest {

    @Mock private InvestmentRepository investmentRepository;
    @Mock private CommercialDetailsRepository commercialDetailsRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private EmailService emailService;
    @Mock private NotificationService notificationService;

    private InvestmentService service;

    private static final Long COMMERCIAL_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new InvestmentService(investmentRepository, commercialDetailsRepository, walletRepository,
                emailService, notificationService);
    }

    private CommercialDetails commercialWithPlan(Plan plan) {
        CommercialDetails commercial = new CommercialDetails();
        commercial.setId(COMMERCIAL_ID);
        commercial.setCompanyName("Comercial S.A.S");
        commercial.setCurrentPlan(plan);
        User user = new User();
        user.setId(9L);
        user.setEmail("comercial@example.com");
        commercial.setUser(user);
        return commercial;
    }

    private Plan plan(PlanCode code) {
        return Plan.builder().code(code).name(code.name()).saleCommissionPct(10).build();
    }

    private Wallet walletWithBalance(long balanceCents) {
        Wallet wallet = new Wallet();
        wallet.setBalanceCents(balanceCents);
        return wallet;
    }

    @Nested
    @DisplayName("createInvestment")
    class CreateInvestment {

        @Test
        @DisplayName("commercial no encontrado: lanza ValidationException")
        void commercialNotFound_throwsValidationException() {
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.createInvestment(COMMERCIAL_ID, BigDecimal.valueOf(2_000_000)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("commercial sin plan activo (currentPlan == null): lanza ValidationException")
        void noCurrentPlan_throwsValidationException() {
            CommercialDetails commercial = commercialWithPlan(null);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            assertThatThrownBy(() -> service.createInvestment(COMMERCIAL_ID, BigDecimal.valueOf(2_000_000)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("sin wallet previo: lo crea on-the-fly antes de depositar")
        void noExistingWallet_createsWalletOnTheFly() {
            CommercialDetails commercial = commercialWithPlan(plan(PlanCode.STANDARD));
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.empty());
            when(walletRepository.save(any(Wallet.class))).thenAnswer(inv -> inv.getArgument(0));

            service.createInvestment(COMMERCIAL_ID, BigDecimal.valueOf(2_000_000));

            ArgumentCaptor<Wallet> walletCaptor = ArgumentCaptor.forClass(Wallet.class);
            verify(walletRepository, org.mockito.Mockito.atLeastOnce()).save(walletCaptor.capture());
            Wallet createdWallet = walletCaptor.getAllValues().get(0);
            assertThat(createdWallet.getCommercial()).isSameAs(commercial);
        }

        @Test
        @DisplayName("saldo total resultante (existente + depósito) menor al mínimo de 1.000.000 COP: lanza ValidationException con el total")
        void belowMinimumTotal_throwsValidationExceptionWithTotal() {
            CommercialDetails commercial = commercialWithPlan(plan(PlanCode.STANDARD));
            Wallet wallet = walletWithBalance(0L);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> service.createInvestment(COMMERCIAL_ID, BigDecimal.valueOf(500_000)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("500000");

            verify(investmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("depósito con más de 2 decimales (fracción de centavo): lanza ValidationException, no ArithmeticException cruda")
        void nonExactCents_throwsValidationException() {
            CommercialDetails commercial = commercialWithPlan(plan(PlanCode.STANDARD));
            Wallet wallet = walletWithBalance(0L);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            assertThatThrownBy(() -> service.createInvestment(COMMERCIAL_ID, new BigDecimal("1000000.505")))
                    .isInstanceOf(ValidationException.class);

            verify(investmentRepository, never()).save(any());
        }

        @Test
        @DisplayName("camino feliz: deposita en el wallet, persiste Investment con snapshot del plan, retorna el DTO")
        void happyPath_depositsAndPersistsInvestment() {
            Plan currentPlan = plan(PlanCode.STANDARD);
            CommercialDetails commercial = commercialWithPlan(currentPlan);
            Wallet wallet = walletWithBalance(0L);
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));
            when(walletRepository.findByCommercialId(COMMERCIAL_ID)).thenReturn(Optional.of(wallet));

            InvestmentResponseDTO response = service.createInvestment(COMMERCIAL_ID, BigDecimal.valueOf(1_000_000));

            assertThat(wallet.getBalanceCents()).isEqualTo(100_000_000L);
            verify(walletRepository).save(wallet);

            ArgumentCaptor<Investment> captor = ArgumentCaptor.forClass(Investment.class);
            verify(investmentRepository).save(captor.capture());
            Investment saved = captor.getValue();
            assertThat(saved.getWallet()).isSameAs(wallet);
            assertThat(saved.getPlanAtDeposit()).isSameAs(currentPlan);
            assertThat(saved.getDepositAmountCents()).isEqualTo(100_000_000L);

            assertThat(response.getDepositAmountCOP()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000));
        }
    }

    @Nested
    @DisplayName("handleWalletExhausted")
    class HandleWalletExhausted {

        @Test
        @DisplayName("comercial inexistente: no-op silencioso, sin lanzar excepción ni notificar")
        void commercialNotFound_isNoOp() {
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.empty());

            service.handleWalletExhausted(COMMERCIAL_ID);

            verifyNoInteractions(emailService);
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("comercial existente: envía email y notificación interna de saldo agotado")
        void commercialFound_sendsEmailAndNotification() {
            CommercialDetails commercial = commercialWithPlan(plan(PlanCode.STANDARD));
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            service.handleWalletExhausted(COMMERCIAL_ID);

            verify(emailService).sendBudgetExhaustedEmail(eq("comercial@example.com"), eq("Comercial S.A.S"));
            verify(notificationService).createInternalNotification(eq(9L), anyString(), anyString(), any());
        }
    }

    @Nested
    @DisplayName("handleWalletReplenished")
    class HandleWalletReplenished {

        @Test
        @DisplayName("comercial inexistente: no-op silencioso, sin lanzar excepción ni notificar")
        void commercialNotFound_isNoOp() {
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.empty());

            service.handleWalletReplenished(COMMERCIAL_ID);

            verifyNoInteractions(emailService);
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("comercial existente: envía email y notificación interna de saldo restaurado")
        void commercialFound_sendsEmailAndNotification() {
            CommercialDetails commercial = commercialWithPlan(plan(PlanCode.STANDARD));
            when(commercialDetailsRepository.findById(COMMERCIAL_ID)).thenReturn(Optional.of(commercial));

            service.handleWalletReplenished(COMMERCIAL_ID);

            verify(emailService).sendBudgetReplenishedEmail(eq("comercial@example.com"), eq("Comercial S.A.S"));
            verify(notificationService).createInternalNotification(eq(9L), anyString(), anyString(), any());
        }
    }
}
