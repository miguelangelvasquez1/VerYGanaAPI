package com.verygana2.services.finance;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.config.wompi.WompiPayoutConfig;
import com.verygana2.dtos.wompi.WompiPayoutRequestDTO;
import com.verygana2.dtos.wompi.WompiPayoutRequestDTO.WompiPayoutTransactionDTO;
import com.verygana2.dtos.wompi.WompiPayoutResponseDTO;
import com.verygana2.models.enums.finance.PayoutStatus;
import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.Payout;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.finance.WompiTransaction;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.PayoutMethodRepository;
import com.verygana2.repositories.finance.PayoutRepository;
import com.verygana2.repositories.finance.WompiTransactionRepository;
import com.verygana2.services.interfaces.finance.PayoutExecutionService;
import com.verygana2.services.wompi.WompiPayoutClient;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayoutExecutionServiceImpl implements PayoutExecutionService {

    private final PayoutRepository payoutRepository;
    private final PayoutMethodRepository payoutMethodRepository;
    private final WompiPayoutClient wompiPayoutClient;
    private final WompiTransactionRepository wompiTransactionRepository;
    private final WompiPayoutConfig wompiPayoutConfig;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutStatus executeScheduledPayout(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Payout no encontrado: " + payoutId));

        processOnePayout(payout, "PAYOUT-SCHEDULER");
        return payout.getStatus();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PayoutStatus executeRetry(UUID payoutId) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Payout no encontrado: " + payoutId));

        int maxRetries = wompiPayoutConfig.getPayout().getMaxRetries();
        if (payout.getRetryCount() >= maxRetries) {
            // Sin este tope, un payout con una falla estructural (ej. cuenta bancaria mal
            // cargada que nadie corrige) se reintentaría cada noche para siempre, gastando
            // llamadas a Wompi sin que nadie se entere a menos que revise el panel activamente.
            payout.setStatus(PayoutStatus.EXHAUSTED);
            payoutRepository.save(payout);
            log.error("[PAYOUT-RETRY] Payout {} alcanzó el máximo de {} reintentos. Marcado EXHAUSTED, "
                    + "requiere intervención manual. Último motivo de fallo: {}",
                    payoutId, maxRetries, payout.getFailureReason());
            return PayoutStatus.EXHAUSTED;
        }

        payout.setRetryCount(payout.getRetryCount() + 1);
        payout.setStatus(PayoutStatus.SCHEDULED);
        payout.setFailureReason(null);
        payoutRepository.save(payout);

        processOnePayout(payout, "PAYOUT-RETRY");
        return payout.getStatus();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markInsufficientBalance(UUID payoutId, long neededCents, long availableCents) {
        Payout payout = payoutRepository.findById(payoutId)
                .orElseThrow(() -> new EntityNotFoundException("Payout no encontrado: " + payoutId));

        payout.setStatus(PayoutStatus.FAILED);
        payout.setFailureReason(String.format(
                "Balance insuficiente en la cuenta de dispersión de Wompi: se requieren %d centavos, disponibles %d centavos.",
                neededCents, availableCents));
        payoutRepository.save(payout);

        log.error("[PAYOUT-SCHEDULER] Payout {} FAILED sin llamar a Wompi: balance insuficiente "
                + "(requiere {} centavos, disponible {} centavos).", payoutId, neededCents, availableCents);
    }

    // ─── Privados ─────────────────────────────────────────────────────────────

    private void processOnePayout(Payout payout, String logPrefix) {
        try {
            sendToWompi(payout);
        } catch (IllegalStateException e) {
            // Estado de negocio esperado (ej. sin método de pago verificado), no
            // un bug — se registra sin stacktrace para no ensuciar los logs con
            // ruido que parece un error del sistema cuando no lo es.
            log.warn("[{}] Payout {} no procesado: {}", logPrefix, payout.getId(), e.getMessage());
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(e.getMessage());
            payoutRepository.save(payout);
        } catch (Exception e) {
            log.error("[{}] Error procesando payout {}: {}", logPrefix, payout.getId(), e.getMessage(), e);
            payout.setStatus(PayoutStatus.FAILED);
            payout.setFailureReason(e.getMessage());
            payoutRepository.save(payout);
        }
    }

    private void sendToWompi(Payout payout) {
        CommercialDetails commercial = payout.getCommercial();

        PayoutMethod method = resolvePayoutMethod(commercial);

        String internalReference = payout.getId().toString();

        // Paso único — Wompi no requiere tokenizar la cuenta antes de transferir
        WompiPayoutRequestDTO request = buildPayoutRequest(payout, commercial, method, internalReference);
        WompiPayoutResponseDTO response = wompiPayoutClient.createPayout(request);

        WompiTransactionStatus initialStatus = response.isAccepted()
                ? WompiTransactionStatus.PENDING
                : WompiTransactionStatus.ERROR;

        WompiTransaction tx = WompiTransaction.builder()
                .wompiId(response.getPayoutId() != null ? response.getPayoutId() : internalReference)
                .type(WompiTransactionType.TRANSFER_PAYOUT)
                .amountInCents(payout.getNetAmountCents())
                .status(initialStatus)
                .reference(internalReference)
                .build();

        tx = wompiTransactionRepository.save(tx);

        payout.setWompiTransaction(tx);
        payout.setStatus(response.isAccepted() ? PayoutStatus.PROCESSING : PayoutStatus.FAILED);
        if (!response.isAccepted()) payout.setFailureReason(response.getCode() + ": " + response.getMessage());
        payoutRepository.save(payout);

        log.info("[PAYOUT-SCHEDULER] Payout → {}: id={}, commercial={}, wompiId={}",
                payout.getStatus(), payout.getId(), commercial.getCompanyName(), response.getPayoutId());
    }

    /**
     * Usa el método marcado como default si sigue VERIFIED y activo; si no hay
     * default (dato legado de antes de que existiera este concepto) o quedó
     * inválido, cae al primer VERIFIED activo como antes.
     */
    private PayoutMethod resolvePayoutMethod(CommercialDetails commercial) {
        PayoutMethod defaultMethod = commercial.getDefaultPayoutMethod();
        if (defaultMethod != null && defaultMethod.canBeUsedForPayout()) {
            return defaultMethod;
        }

        return payoutMethodRepository
                .findFirstByCommercialIdAndVerificationStatusAndActiveTrue(
                        commercial.getId(), VerificationStatus.VERIFIED)
                .orElseThrow(() -> new IllegalStateException(
                        "Empresario " + commercial.getId() + " no tiene método de pago verificado."));
    }

    /**
     * Arma el request de POST /payouts para el método de pago del empresario.
     * Para NEQUI/DAVIPLATA el bankId es una constante configurada (no hay
     * selección de banco: el beneficiario solo aporta su número de celular).
     */
    private WompiPayoutRequestDTO buildPayoutRequest(Payout payout, CommercialDetails commercial,
            PayoutMethod method, String internalReference) {

        String bankId;
        String accountNumber;
        String accountType;

        switch (method.getType()) {
            case NEQUI -> {
                bankId = wompiPayoutConfig.getNequiBankId();
                accountNumber = method.getPhoneNumber();
                // Confirmado en sandbox: la API solo acepta AHORROS/CORRIENTE — "DEPOSITO_ELECTRONICO"
                // (documentado en el spec de SwaggerHub) es rechazado con 400. Nequi/Daviplata van como AHORROS.
                accountType = "AHORROS";
            }
            case DAVIPLATA -> {
                bankId = wompiPayoutConfig.getDaviplataBankId();
                accountNumber = method.getPhoneNumber();
                accountType = "AHORROS";
            }
            default -> { // BANK_TRANSFER
                bankId = method.getBankCode();
                accountNumber = method.getAccountNumber();
                accountType = method.getBankAccountType() == PayoutMethod.BankAccountType.CHECKING
                        ? "CORRIENTE"
                        : "AHORROS";
            }
        }

        // JURIDICA cuando el titular se identifica con NIT (empresa), NATURAL en el resto de casos.
        String personType = method.getAccountHolderDocType() == PayoutMethod.DocType.NIT
                ? "JURIDICA"
                : "NATURAL";

        WompiPayoutTransactionDTO transaction = WompiPayoutTransactionDTO.builder()
                .legalIdType(method.getAccountHolderDocType().name())
                .legalId(method.getAccountHolderDoc())
                .bankId(bankId)
                .accountType(accountType)
                .accountNumber(accountNumber)
                .name(method.getAccountHolderName())
                .amount(payout.getNetAmountCents())
                .personType(personType)
                .email(commercial.getUser().getEmail())
                .reference(internalReference)
                .build();

        return WompiPayoutRequestDTO.builder()
                .reference(internalReference)
                .accountId(wompiPayoutConfig.getAccountId())
                .transactions(List.of(transaction))
                .build();
    }
}
