package com.verygana2.services.finance;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.SubmitCashRefundBankDetailsRequestDTO;
import com.verygana2.dtos.finance.responses.CashRefundResponseDTO;
import com.verygana2.exceptions.financeExceptions.InvalidCashRefundStateException;
import com.verygana2.models.User;
import com.verygana2.models.enums.finance.CashRefundStatus;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.finance.PurchaseItemCashRefund;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.repositories.details.AdminDetailsRepository;
import com.verygana2.repositories.finance.PurchaseItemCashRefundRepository;
import com.verygana2.repositories.pqrs.PqrsRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.NotificationService;
import com.verygana2.services.interfaces.finance.CashRefundService;
import com.verygana2.services.interfaces.finance.TreasuryService;
import com.verygana2.utils.pqrs.RequesterNameResolver;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CashRefundServiceImpl implements CashRefundService {

    private final PurchaseItemCashRefundRepository purchaseItemCashRefundRepository;
    private final AdminDetailsRepository adminDetailsRepository;
    private final TreasuryService treasuryService;
    private final PqrsRepository pqrsRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final RequesterNameResolver requesterNameResolver;

    @Override
    @Transactional
    public void submitBankDetails(Long purchaseItemId, Long consumerId, SubmitCashRefundBankDetailsRequestDTO dto) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("PurchaseItem id must be positive");
        }
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        PurchaseItemCashRefund cashRefund = purchaseItemCashRefundRepository.findByPurchaseItemId(purchaseItemId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No pending cash refund found for purchaseItemId: " + purchaseItemId));

        if (!cashRefund.getPurchaseItem().getPurchase().getConsumer().getId().equals(consumerId)) {
            throw new EntityNotFoundException("No pending cash refund found for purchaseItemId: " + purchaseItemId);
        }

        if (cashRefund.getStatus() == CashRefundStatus.PAID) {
            throw new InvalidCashRefundStateException("This refund has already been paid");
        }

        cashRefund.submitBankDetails(dto.getAccountHolderName(), dto.getAccountHolderDoc(),
                dto.getAccountHolderDocType(), dto.getBankName(), dto.getAccountNumber(), dto.getAccountType());
        purchaseItemCashRefundRepository.save(cashRefund);

        log.info("[CASH-REFUND] Datos bancarios recibidos: purchaseItemId={}", purchaseItemId);
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CashRefundResponseDTO> getPendingPayments(Pageable pageable) {
        Page<CashRefundResponseDTO> page = purchaseItemCashRefundRepository
                .findByStatus(CashRefundStatus.PENDING_PAYMENT, pageable)
                .map(this::toResponseDTO);
        return PagedResponse.from(page);
    }

    @Override
    @Transactional
    public void markPaid(UUID cashRefundId, Long adminUserId) {
        PurchaseItemCashRefund cashRefund = purchaseItemCashRefundRepository.findById(cashRefundId)
                .orElseThrow(() -> new EntityNotFoundException("Cash refund not found: " + cashRefundId));

        if (cashRefund.getStatus() == CashRefundStatus.PAID) {
            log.info("[CASH-REFUND] {} ya estaba PAID, no se reprocesa", cashRefundId);
            return;
        }

        if (!cashRefund.hasBankDetails()) {
            throw new InvalidCashRefundStateException("Cannot mark as paid: bank details have not been submitted yet");
        }

        AdminDetails admin = adminDetailsRepository.findById(adminUserId)
                .orElseThrow(() -> new EntityNotFoundException("Admin not found: " + adminUserId));

        treasuryService.registerManualCashRefundPaid(cashRefund.getAmountCents(), cashRefund.getId());

        cashRefund.markPaid(admin);
        purchaseItemCashRefundRepository.save(cashRefund);

        log.info("[CASH-REFUND] {} marcado como PAID por adminUserId={}", cashRefundId, adminUserId);

        if (cashRefund.getPqrs() != null) {
            resolveLinkedPqrs(cashRefund.getPqrs());
        }
    }

    /**
     * Cierra el PQRS que originó este reembolso ahora que el pago manual ya
     * se confirmó — ver Pqrs.status=PENDIENTE_PAGO_REEMBOLSO y
     * PqrsServiceImpl.respondToPqrs. Todo el flujo de reclamo por código
     * queda dentro del mismo PQRS: se resuelve aquí, no cuando el admin
     * aprobó el reembolso.
     */
    private void resolveLinkedPqrs(Pqrs pqrs) {
        pqrs.setStatus(PqrsStatus.RESUELTA);
        pqrs.setResolvedAt(ZonedDateTime.now());
        Pqrs saved = pqrsRepository.save(pqrs);

        User requester = saved.getRequester();
        notificationService.createInternalNotification(
                requester.getId(),
                "Tu PQRS fue resuelto",
                "Radicado " + saved.getBased() + ": tu reembolso ya fue pagado",
                Instant.now());

        emailService.sendPqrsResolved(requester.getEmail(), requesterNameResolver.resolve(requester),
                saved.getBased(), saved.getResponse());

        log.info("[CASH-REFUND] PQRS {} resuelto tras confirmar pago del reembolso", saved.getId());
    }

    private CashRefundResponseDTO toResponseDTO(PurchaseItemCashRefund cashRefund) {
        CashRefundResponseDTO dto = new CashRefundResponseDTO();
        dto.setId(cashRefund.getId());
        dto.setPurchaseItemId(cashRefund.getPurchaseItem().getId());
        dto.setAmountCents(cashRefund.getAmountCents());
        dto.setStatus(cashRefund.getStatus());
        dto.setAccountHolderName(cashRefund.getAccountHolderName());
        dto.setAccountHolderDoc(cashRefund.getAccountHolderDoc());
        dto.setAccountHolderDocType(cashRefund.getAccountHolderDocType());
        dto.setBankName(cashRefund.getBankName());
        dto.setAccountNumber(cashRefund.getAccountNumber());
        dto.setAccountType(cashRefund.getAccountType());
        dto.setCreatedAt(cashRefund.getCreatedAt());
        dto.setBankDetailsSubmittedAt(cashRefund.getBankDetailsSubmittedAt());
        dto.setPaidAt(cashRefund.getPaidAt());
        return dto;
    }
}
