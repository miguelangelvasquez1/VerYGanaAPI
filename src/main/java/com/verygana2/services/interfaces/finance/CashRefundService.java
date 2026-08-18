package com.verygana2.services.interfaces.finance;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.SubmitCashRefundBankDetailsRequestDTO;
import com.verygana2.dtos.finance.responses.CashRefundResponseDTO;

public interface CashRefundService {

    /**
     * El comprador indica a qué cuenta quiere que se le haga la transferencia
     * manual. Solo puede hacerlo el dueño del PurchaseItem, y solo si el
     * reembolso todavía no fue pagado.
     */
    void submitBankDetails(Long purchaseItemId, Long consumerId, SubmitCashRefundBankDetailsRequestDTO dto);

    /** Reembolsos en efectivo pendientes de pago manual, para el panel de admin. */
    PagedResponse<CashRefundResponseDTO> getPendingPayments(Pageable pageable);

    /**
     * El admin confirma que ya hizo la transferencia manual. Dispara
     * TreasuryService.registerManualCashRefundPaid (OPERATIONS → externo).
     */
    void markPaid(UUID cashRefundId, Long adminUserId);
}
