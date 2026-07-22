package com.verygana2.services.raffles;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.dtos.raffle.requests.ClaimPrizeRequestDTO;
import com.verygana2.exceptions.rafflesExceptions.ClaimPrizeException;
import com.verygana2.mappers.raffles.RaffleWinnerMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.raffles.ClaimPreferenceDeliveryMethod;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.security.ClaimCodeEncryptor;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.EmailVerificationService;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.raffles.RaffleResultService;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link RaffleWinnerServiceImpl}: el flujo de reclamo de un premio
 * (dueño correcto, no reclamado, plazo vigente) y los dos canales de entrega
 * (email/SMS) con verificación OTP cuando se usa un contacto alternativo.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RaffleWinnerServiceImpl")
class RaffleWinnerServiceImplTest {

    @Mock private RaffleWinnerRepository raffleWinnerRepository;
    @Mock private PrizeRepository prizeRepository;
    @Mock private RaffleResultService raffleResultService;
    @Mock private RaffleWinnerMapper raffleWinnerMapper;
    @Mock private EmailService emailService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private TwilioSmsService twilioSmsService;
    @Mock private ClaimCodeEncryptor claimCodeEncryptor;

    private RaffleWinnerServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaffleWinnerServiceImpl(raffleWinnerRepository, prizeRepository,
                raffleWinnerMapper, emailService, emailVerificationService, twilioSmsService, claimCodeEncryptor);
    }

    private RaffleWinner winnerRecord(Long winnerConsumerId, boolean claimed, ZonedDateTime deadline) {
        ConsumerDetails winner = new ConsumerDetails();
        winner.setId(winnerConsumerId);
        User user = new User();
        user.setEmail("ganador@test.com");
        user.setPhoneNumber("3001234567");
        winner.setUser(user);

        Prize prize = new Prize();
        prize.setId(1L);
        prize.setClaimCode("cipherText");
        prize.setQuantity(1);
        prize.setClaimedCount(0);

        RaffleWinner winnerRecord = new RaffleWinner();
        winnerRecord.setWinner(winner);
        winnerRecord.setPrize(prize);
        winnerRecord.setPrizeClaimed(claimed);
        winnerRecord.setClaimDeadline(deadline);
        return winnerRecord;
    }

    @Nested
    @DisplayName("claimPrize")
    class ClaimPrize {

        private ClaimPrizeRequestDTO emailRequest() {
            ClaimPrizeRequestDTO request = new ClaimPrizeRequestDTO();
            request.setPrizeId(1L);
            request.setDeliveryMethod(ClaimPreferenceDeliveryMethod.EMAIL);
            return request;
        }

        @Test
        @DisplayName("reclamo válido por email al correo registrado: marca reclamado e incrementa el contador del premio")
        void validEmailClaim_marksClaimedAndIncrementsCount() {
            RaffleWinner winnerRecord = winnerRecord(9L, false, ZonedDateTime.now(ZoneOffset.UTC).plusDays(5));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));
            when(claimCodeEncryptor.decrypt("cipherText")).thenReturn("PLAIN-CODE");

            service.claimPrize(9L, emailRequest());

            assertThat(winnerRecord.isPrizeClaimed()).isTrue();
            assertThat(winnerRecord.getPrizeTrackingInfo()).isEqualTo("EMAIL:ganador@test.com");
            verify(emailService).sendPrizeClaimConfirmation(winnerRecord.getPrize(), "ganador@test.com", "PLAIN-CODE");
            verify(prizeRepository).save(winnerRecord.getPrize());
            assertThat(winnerRecord.getPrize().getClaimedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("usuario autenticado no es el ganador del premio: lanza ClaimPrizeException")
        void notTheWinner_throwsClaimPrizeException() {
            RaffleWinner winnerRecord = winnerRecord(9L, false, ZonedDateTime.now(ZoneOffset.UTC).plusDays(5));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));

            assertThatThrownBy(() -> service.claimPrize(999L, emailRequest())).isInstanceOf(ClaimPrizeException.class);
        }

        @Test
        @DisplayName("premio ya reclamado: lanza ClaimPrizeException")
        void alreadyClaimed_throwsClaimPrizeException() {
            RaffleWinner winnerRecord = winnerRecord(9L, true, ZonedDateTime.now(ZoneOffset.UTC).plusDays(5));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));

            assertThatThrownBy(() -> service.claimPrize(9L, emailRequest())).isInstanceOf(ClaimPrizeException.class);
        }

        @Test
        @DisplayName("plazo de reclamación vencido: lanza ClaimPrizeException")
        void deadlinePassed_throwsClaimPrizeException() {
            RaffleWinner winnerRecord = winnerRecord(9L, false, ZonedDateTime.now(ZoneOffset.UTC).minusDays(1));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));

            assertThatThrownBy(() -> service.claimPrize(9L, emailRequest())).isInstanceOf(ClaimPrizeException.class);
        }

        @Test
        @DisplayName("no existe registro de ganador para el premio: lanza EntityNotFoundException")
        void noWinnerRecord_throwsEntityNotFoundException() {
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.claimPrize(9L, emailRequest())).isInstanceOf(EntityNotFoundException.class);
        }

        @Test
        @DisplayName("correo alternativo sin código OTP: lanza ClaimPrizeException")
        void newEmailWithoutOtp_throwsClaimPrizeException() {
            RaffleWinner winnerRecord = winnerRecord(9L, false, ZonedDateTime.now(ZoneOffset.UTC).plusDays(5));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));

            ClaimPrizeRequestDTO request = emailRequest();
            request.setNewEmail("otro@test.com"); // sin emailOtpCode

            assertThatThrownBy(() -> service.claimPrize(9L, request)).isInstanceOf(ClaimPrizeException.class);
            verify(emailService, never()).sendPrizeClaimConfirmation(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("reclamo por SMS con teléfono alternativo y OTP válido: entrega por SMS")
        void validSmsClaimWithNewPhone_deliversBySms() {
            RaffleWinner winnerRecord = winnerRecord(9L, false, ZonedDateTime.now(ZoneOffset.UTC).plusDays(5));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));
            when(claimCodeEncryptor.decrypt("cipherText")).thenReturn("PLAIN-CODE");
            when(twilioSmsService.verifyOtp("3009999999", "123456")).thenReturn(true);

            ClaimPrizeRequestDTO request = new ClaimPrizeRequestDTO();
            request.setPrizeId(1L);
            request.setDeliveryMethod(ClaimPreferenceDeliveryMethod.SMS);
            request.setNewPhoneNumber("3009999999");
            request.setSmsOtpCode("123456");

            service.claimPrize(9L, request);

            assertThat(winnerRecord.getPrizeTrackingInfo()).isEqualTo("SMS:3009999999");
            verify(twilioSmsService).sendPrizeClaimConfirmation(winnerRecord.getPrize(), "3009999999", "PLAIN-CODE");
        }

        @Test
        @DisplayName("SMS con OTP inválido: lanza ClaimPrizeException y no entrega")
        void invalidSmsOtp_throwsClaimPrizeException() {
            RaffleWinner winnerRecord = winnerRecord(9L, false, ZonedDateTime.now(ZoneOffset.UTC).plusDays(5));
            when(raffleWinnerRepository.findByPrizeId(1L)).thenReturn(Optional.of(winnerRecord));
            when(twilioSmsService.verifyOtp("3009999999", "000000")).thenReturn(false);

            ClaimPrizeRequestDTO request = new ClaimPrizeRequestDTO();
            request.setPrizeId(1L);
            request.setDeliveryMethod(ClaimPreferenceDeliveryMethod.SMS);
            request.setNewPhoneNumber("3009999999");
            request.setSmsOtpCode("000000");

            assertThatThrownBy(() -> service.claimPrize(9L, request)).isInstanceOf(ClaimPrizeException.class);
            verify(twilioSmsService, never()).sendPrizeClaimConfirmation(any(), anyString(), anyString());
        }
    }

    @Test
    @DisplayName("expireOverduePrizes: marca EXPIRED todos los premios pendientes vencidos y retorna la cantidad")
    void expireOverduePrizes_marksExpiredAndReturnsCount() {
        Prize overdue1 = new Prize();
        Prize overdue2 = new Prize();
        when(prizeRepository.findOverdueUnclaimedPrizes(any())).thenReturn(java.util.List.of(overdue1, overdue2));

        int count = service.expireOverduePrizes();

        assertThat(count).isEqualTo(2);
        assertThat(overdue1.getPrizeStatus()).isEqualTo(com.verygana2.models.enums.raffles.PrizeStatus.EXPIRED);
        assertThat(overdue2.getPrizeStatus()).isEqualTo(com.verygana2.models.enums.raffles.PrizeStatus.EXPIRED);
        verify(prizeRepository).saveAll(java.util.List.of(overdue1, overdue2));
    }
}
