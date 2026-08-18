package com.verygana2.controllers.marketplace;


import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.finance.requests.SubmitCashRefundBankDetailsRequestDTO;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.pqrs.responses.PqrsResponseDTO;
import com.verygana2.dtos.purchase.requests.ClaimPurchaseItemRequestDTO;
import com.verygana2.dtos.purchase.requests.ReportPurchaseItemRequestDTO;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.services.interfaces.finance.CashRefundService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemService;
import com.verygana2.services.interfaces.pqrs.PqrsService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/purchaseItems")
@RequiredArgsConstructor
public class PurchaseItemController {

    private final PurchaseItemService purchaseItemService;
    private final PqrsService pqrsService;
    private final CashRefundService cashRefundService;

    @GetMapping("/totalSales")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<Long> getTotalCommercialSales(@AuthenticationPrincipal Jwt jwt){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseItemService.getTotalSalesbyCommercial(commercialId));
    }

    @GetMapping("/topSelling")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<PagedResponse<FeaturedProductResponseDTO>> getTopSellingProductsPage (@AuthenticationPrincipal Jwt jwt, @PageableDefault(size = 5, page = 0) Pageable pageable){
        Long commercialId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseItemService.getTopSellingProductsPage(commercialId, pageable));
    }

    @GetMapping("/{purchaseItemId}/delivered-code")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<String> getDeliveredCode (@AuthenticationPrincipal Jwt jwt,
            @PathVariable("purchaseItemId") Long purchaseItemId){
        Long consumerId = jwt.getClaim("userId");
        return ResponseEntity.ok(purchaseItemService.getDeliveredCode(purchaseItemId, consumerId));
    }

    /**
     * El comerciante valida el PIN que el comprador le entrega al momento de
     * la entrega física del producto. Solo aplica a ítems de productos
     * PHYSICAL — ver PurchaseItemServiceImpl.claimPhysicalItem.
     */
    @PostMapping("/{purchaseItemId}/claim")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<Void> claimPhysicalItem(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("purchaseItemId") Long purchaseItemId,
            @Valid @RequestBody ClaimPurchaseItemRequestDTO request) {
        Long commercialId = jwt.getClaim("userId");
        purchaseItemService.claimPhysicalItem(purchaseItemId, commercialId, request.getPin());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    /**
     * El comprador reporta un problema con un ítem de su compra (código
     * inválido, no entregado, no corresponde a lo comprado). Crea un PQRS ya
     * vinculado al ítem — ver PqrsService.createPqrsForPurchaseItem. Mientras
     * ese PQRS no se resuelva, el ítem queda excluido del payout diario.
     */
    @PostMapping("/{purchaseItemId}/report")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<PqrsResponseDTO> reportIssue(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("purchaseItemId") Long purchaseItemId,
            @Valid @RequestBody ReportPurchaseItemRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        PurchaseItem item = purchaseItemService.getReportableItem(purchaseItemId, consumerId);
        PqrsResponseDTO response = pqrsService.createPqrsForPurchaseItem(
                item, request.getReason(), request.getDescription(), consumerId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * El comprador indica a qué cuenta quiere que se le haga la transferencia
     * manual de un reembolso en efectivo ya aprobado (PQRS resuelto con
     * REFUND) — ver CashRefundService.
     */
    @PostMapping("/{purchaseItemId}/cash-refund/bank-details")
    @PreAuthorize("hasRole('CONSUMER')")
    public ResponseEntity<Void> submitCashRefundBankDetails(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("purchaseItemId") Long purchaseItemId,
            @Valid @RequestBody SubmitCashRefundBankDetailsRequestDTO request) {
        Long consumerId = jwt.getClaim("userId");
        cashRefundService.submitBankDetails(purchaseItemId, consumerId, request);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}