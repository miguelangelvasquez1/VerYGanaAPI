package com.verygana2.services.finance;

import java.time.Instant;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.CreatePayoutMethodRequestDTO;
import com.verygana2.dtos.finance.responses.PayoutMethodResponseDTO;
import com.verygana2.dtos.generic.EntityCreatedResponseDTO;
import com.verygana2.exceptions.payoutExceptions.InvalidPayoutMethodStateException;
import com.verygana2.exceptions.payoutExceptions.OtpVerificationException;
import com.verygana2.exceptions.payoutExceptions.PayoutMethodNotFoundException;
import com.verygana2.mappers.finance.PayoutMethodMapper;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.PayoutMethodType;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.services.interfaces.TwilioSmsService;
import com.verygana2.services.interfaces.compliance.ScreeningService;
import com.verygana2.services.interfaces.details.CommercialDetailsService;
import com.verygana2.services.interfaces.finance.PayoutMethodService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutMethodServiceImpl implements PayoutMethodService {

    private final CommercialDetailsService commercialDetailsService;
    private final PayoutMethodRepository payoutMethodRepository;
    private final PayoutMethodMapper payoutMethodMapper;
    private final TwilioSmsService twilioSmsService;
    private final ScreeningService screeningService;

    // ===== COMMERCIAL =====

    @Override
    @Transactional
    public EntityCreatedResponseDTO createPayoutMethod(Long commercialId, CreatePayoutMethodRequestDTO request) {
        CommercialDetails commercial = commercialDetailsService.getCommercialById(commercialId);

        validateFieldsByType(request);

        PayoutMethod method = payoutMethodMapper.toPayoutMethod(request);
        method.setCommercial(commercial);

        if (request.getType() == PayoutMethodType.BANK_TRANSFER) {
            // BANK_TRANSFER va directo a revisión manual del admin
            method.setVerificationStatus(VerificationStatus.UNDER_REVIEW);
        }

        PayoutMethod saved = payoutMethodRepository.save(method);

        // Para NEQUI/DAVIPLATA, se dispara el OTP automáticamente
        if (request.getType() == PayoutMethodType.NEQUI || request.getType() == PayoutMethodType.DAVIPLATA) {
            try {
                twilioSmsService.sendOtp(saved.getPhoneNumber());
                saved.setVerificationStatus(VerificationStatus.AWAITING_OTP);
                payoutMethodRepository.save(saved);
                log.info("OTP enviado para método de pago id={}", saved.getId());
            } catch (Exception e) {
                log.error("Error enviando OTP para método id={}: {}", saved.getId(), e.getMessage());
                // El método queda en PENDING_VERIFICATION; el commercial puede usar /resend-otp
            }
        }

        String message = switch (request.getType()) {
            case NEQUI, DAVIPLATA ->
                "Método registrado. Revisa el SMS en tu número registrado para confirmar el código OTP.";
            case BANK_TRANSFER ->
                "Método registrado. Quedará disponible una vez que nuestro equipo verifique los datos bancarios.";
        };

        return new EntityCreatedResponseDTO(saved.getId(), message, Instant.now());
    }

    @Override
    @Transactional
    public void verifyOtp(Long commercialId, Long payoutMethodId, String code) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        if (method.getVerificationStatus() != VerificationStatus.AWAITING_OTP) {
            throw new InvalidPayoutMethodStateException(
                "El método no está esperando verificación OTP. Estado actual: " + method.getVerificationStatus());
        }

        boolean approved = twilioSmsService.verifyOtp(method.getPhoneNumber(), code);

        if (!approved) {
            throw new OtpVerificationException(
                "Código OTP incorrecto o expirado. Solicita un nuevo código con /resend-otp.");
        }

        method.markVerified();
        payoutMethodRepository.save(method);
        log.info("Método de pago id={} verificado vía OTP para commercial id={}", payoutMethodId, commercialId);
    }

    @Override
    @Transactional
    public void resendOtp(Long commercialId, Long payoutMethodId) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);

        if (method.getVerificationStatus() != VerificationStatus.AWAITING_OTP
                && method.getVerificationStatus() != VerificationStatus.PENDING_VERIFICATION) {
            throw new InvalidPayoutMethodStateException(
                "Solo se puede reenviar el OTP si el método está en estado AWAITING_OTP o PENDING_VERIFICATION.");
        }

        twilioSmsService.sendOtp(method.getPhoneNumber());
        method.setVerificationStatus(VerificationStatus.AWAITING_OTP);
        payoutMethodRepository.save(method);
        log.info("OTP reenviado para método id={}", payoutMethodId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PayoutMethodResponseDTO> getByCommercialId(Long commercialId, Pageable pageable) {
        return PagedResponse.from(
            payoutMethodRepository.findByCommercialId(commercialId, pageable)
                .map(payoutMethodMapper::toPayoutMethodResponseDTO)
        );
    }

    @Override
    @Transactional
    public void deactivatePayoutMethod(Long commercialId, Long payoutMethodId) {
        PayoutMethod method = getOwnedMethod(commercialId, payoutMethodId);
        method.setActive(false);
        payoutMethodRepository.save(method);
        log.info("Método de pago id={} desactivado por commercial id={}", payoutMethodId, commercialId);
    }

    // ===== ADMIN =====

    @Override
    @Transactional
    public void adminVerifyMethod(Long payoutMethodId) {
        PayoutMethod method = payoutMethodRepository.findById(payoutMethodId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));

        if (method.getVerificationStatus() != VerificationStatus.UNDER_REVIEW) {
            throw new InvalidPayoutMethodStateException(
                "Solo se pueden verificar métodos en estado UNDER_REVIEW. Estado actual: "
                + method.getVerificationStatus());
        }

        Long commercialUserId = method.getCommercial().getUser().getId();
        try {
            screeningService.screenOrThrow(
                    commercialUserId,
                    method.getAccountHolderName(),
                    method.getAccountHolderDoc());
        } catch (com.verygana2.exceptions.compliance.ScreeningHitException e) {
            method.reject("Rechazado automáticamente por screening: " + e.getMessage());
            payoutMethodRepository.save(method);
            throw new IllegalStateException("Método de pago rechazado: el titular aparece en listas restrictivas.");
        }

        method.markVerified();
        payoutMethodRepository.save(method);
        log.info("Admin verificó método de pago id={}", payoutMethodId);
    }

    @Override
    @Transactional
    public void adminRejectMethod(Long payoutMethodId, String reason) {
        PayoutMethod method = payoutMethodRepository.findById(payoutMethodId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));

        method.reject(reason);
        payoutMethodRepository.save(method);
        log.info("Admin rechazó método de pago id={} — motivo: {}", payoutMethodId, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<PayoutMethodResponseDTO> getByStatus(VerificationStatus status, Pageable pageable) {
        return PagedResponse.from(
            payoutMethodRepository.findByVerificationStatus(status, pageable)
                .map(payoutMethodMapper::toPayoutMethodResponseDTO)
        );
    }

    // ===== HELPERS =====

    private PayoutMethod getOwnedMethod(Long commercialId, Long payoutMethodId) {
        return payoutMethodRepository.findByIdAndCommercialId(payoutMethodId, commercialId)
                .orElseThrow(() -> new PayoutMethodNotFoundException(payoutMethodId));
    }

    /**
     * Valida que los campos requeridos por cada tipo de método estén presentes.
     * Wompi rechaza con D07/D11/D34 si falta alguno de estos datos.
     */
    private void validateFieldsByType(CreatePayoutMethodRequestDTO req) {
        switch (req.getType()) {
            case BANK_TRANSFER -> {
                if (req.getBankCode() == null || req.getBankCode().isBlank())
                    throw new IllegalArgumentException("bankCode es requerido para BANK_TRANSFER");
                if (req.getAccountNumber() == null || req.getAccountNumber().isBlank())
                    throw new IllegalArgumentException("accountNumber es requerido para BANK_TRANSFER");
                if (req.getBankAccountType() == null)
                    throw new IllegalArgumentException("bankAccountType es requerido para BANK_TRANSFER");
            }
            case NEQUI, DAVIPLATA -> {
                if (req.getPhoneNumber() == null || req.getPhoneNumber().isBlank())
                    throw new IllegalArgumentException("phoneNumber es requerido para " + req.getType());
                if (!req.getPhoneNumber().replaceAll("[^0-9]", "").matches("^3\\d{9}$"))
                    throw new IllegalArgumentException(
                        "phoneNumber debe ser un número colombiano válido de 10 dígitos (ej: 3001234567)");
            }
        }
    }
}
