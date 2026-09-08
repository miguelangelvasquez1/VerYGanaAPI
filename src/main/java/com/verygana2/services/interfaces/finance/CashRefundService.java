package com.verygana2.services.interfaces.finance;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.SubmitCashRefundBankDetailsRequestDTO;
import com.verygana2.dtos.finance.responses.CashRefundResponseDTO;
import com.verygana2.models.enums.finance.CashRefundStatus;

public interface CashRefundService {

    /**
     * El comprador indica a qué cuenta quiere que se le haga la transferencia
     * manual. Solo puede hacerlo el dueño del PurchaseItem, y solo si el
     * reembolso todavía no fue pagado.
     */
    void submitBankDetails(Long purchaseItemId, Long consumerId, SubmitCashRefundBankDetailsRequestDTO dto);

    PagedResponse<CashRefundResponseDTO> getRefunds(CashRefundStatus status, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);

    /**
     * Reembolso en efectivo asociado a un PurchaseItem, si existe. Pensado para
     * el detalle de un PQRS: si el PQRS trae purchaseItemId, el panel de admin
     * consulta esto para mostrar los datos bancarios y permitir marcarlo pagado
     * sin salir de la solicitud.
     */
    Optional<CashRefundResponseDTO> findByPurchaseItemId(Long purchaseItemId);

    /**
     * El admin confirma que ya hizo la transferencia manual. Dispara
     * TreasuryService.registerManualCashRefundPaid (OPERATIONS → externo).
     */
    void markPaid(UUID cashRefundId, Long adminUserId);
}
