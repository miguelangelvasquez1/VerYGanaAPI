package com.verygana2.services.raffles;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.raffle.requests.ClaimPrizeRequestDTO;
import com.verygana2.dtos.raffle.responses.PrizeWonResponseDTO;
import com.verygana2.dtos.raffle.responses.WinnerSummaryResponseDTO;
import com.verygana2.exceptions.EmailVerificationException;
import com.verygana2.exceptions.rafflesExceptions.ClaimPrizeException;
import com.verygana2.mappers.raffles.RaffleWinnerMapper;
import com.verygana2.models.User;
import com.verygana2.models.enums.raffles.ClaimPreferenceDeliveryMethod;
import com.verygana2.models.enums.raffles.PrizeStatus;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.RaffleWinner;
import com.verygana2.repositories.raffles.PrizeRepository;
import com.verygana2.repositories.raffles.RaffleWinnerRepository;
import com.verygana2.security.CodeEncryptor;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.EmailVerificationService;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.raffles.RaffleWinnerService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RaffleWinnerServiceImpl implements RaffleWinnerService {

    private final RaffleWinnerRepository raffleWinnerRepository;
    private final PrizeRepository prizeRepository;
    private final RaffleWinnerMapper raffleWinnerMapper;
    private final EmailService emailService;
    private final EmailVerificationService emailVerificationService;
    private final TwilioSmsService twilioSmsService;
    @Qualifier("claimCodeEncryptor")
    private final CodeEncryptor claimCodeEncryptor;
    private static final String domain = "https://cdn.verygana.com/public/";

    @Transactional(readOnly = true)
    @Override
    public PagedResponse<PrizeWonResponseDTO> getWonPrizesList(Long consumerId, PrizeStatus status, Pageable pageable) {
        Page<RaffleWinner> wins = raffleWinnerRepository.findWonPrizesByConsumer(consumerId, status, pageable);
        return PagedResponse.from(wins.map(w -> {
            Prize prize = w.getPrize();
            return PrizeWonResponseDTO.builder()
                    .prizeId(prize.getId())
                    .winnerId(w.getWinner().getId())
                    .title(prize.getTitle())
                    .description(prize.getDescription())
                    .brand(prize.getBrand())
                    .value(prize.getValue())
                    .imageUrl(domain + prize.getImageAsset().getObjectKey())
                    .prizeType(prize.getPrizeType())
                    .position(prize.getPosition())
                    .quantity(prize.getQuantity())
                    .ticketWinnerNumber(w.getWinningTicket().getTicketNumber())
                    .drawnAt(w.getRaffleResult().getDrawnAt())
                    .isClaimed(w.isPrizeClaimed())
                    .claimedAt(w.getPrizeClaimedAt())
                    .build();
        }));
    }

    @Override
    public List<WinnerSummaryResponseDTO> getLastRaffleWinners() {
        List<RaffleWinner> winners = raffleWinnerRepository.findLastWinners();
        return winners.stream().map(raffleWinnerMapper::toWinnerSummaryResponseDTO).toList();
    }

    @Override
    public void claimPrize(Long consumerId, ClaimPrizeRequestDTO request) {

        // 1. Buscar registro del ganador
        RaffleWinner raffleWinner = raffleWinnerRepository.findByPrizeId(request.getPrizeId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "No winner record found for prize id: " + request.getPrizeId()));

        // 2. Verificar que el usuario autenticado es el ganador
        if (!raffleWinner.getWinner().getId().equals(consumerId)) {
            throw new ClaimPrizeException("You are not the winner of this prize");
        }

        // 3. Verificar que no haya sido reclamado previamente
        if (raffleWinner.isPrizeClaimed()) {
            throw new ClaimPrizeException("Prize has already been claimed");
        }

        // 4. Verificar que el plazo de reclamación no haya expirado
        if (ZonedDateTime.now(ZoneOffset.UTC).isAfter(raffleWinner.getClaimDeadline())) {
            throw new ClaimPrizeException(
                    "Claim deadline has passed. Prize expired on: " + raffleWinner.getClaimDeadline());
        }

        Prize prize = raffleWinner.getPrize();
        User user = raffleWinner.getWinner().getUser();

        // 5. Descifrar el código de reclamación
        String decryptedCode = claimCodeEncryptor.decrypt(prize.getClaimCode());

        // 6. Determinar canal de entrega y enviar
        String deliveryRef = deliver(request, prize, user, decryptedCode);

        // 7. Persistir estado de reclamación
        raffleWinner.setPrizeClaimed(true);
        raffleWinner.setPrizeClaimedAt(ZonedDateTime.now(ZoneOffset.UTC));
        raffleWinner.setPrizeTrackingInfo(deliveryRef);
        raffleWinnerRepository.save(raffleWinner);

        prize.incrementClaimedCount();
        prizeRepository.save(prize);

        log.info("Prize claimed. consumer={}, prize={}, method={}, ref={}",
                consumerId, prize.getId(), request.getDeliveryMethod(), deliveryRef);
    }

    @Override
    public int expireOverduePrizes() {
        List<Prize> overduePrizes = prizeRepository.findOverdueUnclaimedPrizes(ZonedDateTime.now(ZoneOffset.UTC));

        for (Prize prize : overduePrizes) {
            prize.setPrizeStatus(PrizeStatus.EXPIRED);
        }
        prizeRepository.saveAll(overduePrizes);

        if (!overduePrizes.isEmpty()) {
            log.info("Prizes expired by deadline: {}", overduePrizes.stream().map(Prize::getId).toList());
        }

        return overduePrizes.size();
    }

    private String deliver(ClaimPrizeRequestDTO request, Prize prize, User user, String decryptedCode) {
        if (request.getDeliveryMethod() == ClaimPreferenceDeliveryMethod.EMAIL) {
            String targetEmail = hasValue(request.getNewEmail()) ? request.getNewEmail() : user.getEmail();

            // Si usa un correo alternativo, verificar el código de verificación primero
            if (hasValue(request.getNewEmail())) {
                if (!hasValue(request.getEmailOtpCode())) {
                    throw new ClaimPrizeException("Email verification code is required to verify a new email");
                }
                try {
                    emailVerificationService.verifyCode(request.getNewEmail(), request.getEmailOtpCode());
                } catch (EmailVerificationException e) {
                    throw new ClaimPrizeException(e.getMessage());
                }
            }

            emailService.sendPrizeClaimConfirmation(prize, targetEmail, decryptedCode);
            return "EMAIL:" + targetEmail;
        }

        if (request.getDeliveryMethod() == ClaimPreferenceDeliveryMethod.SMS) {
            String targetPhone = hasValue(request.getNewPhoneNumber()) ? request.getNewPhoneNumber() : user.getPhoneNumber();

            // Si usa un número alternativo, verificar OTP de Twilio primero
            if (hasValue(request.getNewPhoneNumber())) {
                if (!hasValue(request.getSmsOtpCode())) {
                    throw new ClaimPrizeException("SMS OTP code is required to verify a new phone number");
                }
                boolean verified = twilioSmsService.verifyOtp(request.getNewPhoneNumber(), request.getSmsOtpCode());
                if (!verified) {
                    throw new ClaimPrizeException("SMS verification code is invalid or has expired");
                }
            }

            twilioSmsService.sendPrizeClaimConfirmation(prize, targetPhone, decryptedCode);
            return "SMS:" + targetPhone;
        }

        throw new ClaimPrizeException("Unsupported delivery method: " + request.getDeliveryMethod());
    }

    private boolean hasValue(String s) {
        return s != null && !s.isBlank();
    }
}
