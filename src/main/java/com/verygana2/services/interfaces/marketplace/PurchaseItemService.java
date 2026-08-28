package com.verygana2.services.interfaces.marketplace;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.dtos.purchase.responses.CommercialPendingClaimResponseDTO;
import com.verygana2.dtos.user.commercial.responses.DailySaleResponseDTO;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.marketplace.PurchaseItem;

public interface PurchaseItemService {
    Long getTotalSalesbyCommercial (Long commercialId);
    List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId);
    boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId);
    PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId);
    PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long commercialId, Pageable pageable);
    String getDeliveredCode (Long purchaseItemId, Long consumerId);

    /**
     * Valida el PIN de reclamación física que el comprador le entrega al
     * comerciante al momento de recibir el producto. Solo aplica a ítems de
     * productos PHYSICAL. Idempotente: si el ítem ya está CLAIMED, no hace
     * nada (reintentos de red no deben fallar ni reprocesar).
     */
    void claimPhysicalItem(Long purchaseItemId, Long commercialId, String pin);

    PagedResponse<CommercialPendingClaimResponseDTO> getPendingClaims (Long commercialId, DocumentType documentType, String documentNumber, Pageable pageable);
    /**
     * Valida que un PurchaseItem pueda reportarse (POST /purchaseItems/{id}/report):
     * pertenece al consumidor autenticado, no está REFUNDED/CANCELLED, y si ya
     * está CLAIMED debe estar dentro de la ventana de reporte post-reclamo.
     * No crea el PQRS — solo devuelve el ítem validado, listo para que el
     * llamador (PurchaseItemController) invoque PqrsService.createPqrsForPurchaseItem.
     */
    PurchaseItem getReportableItem(Long purchaseItemId, Long consumerId);

    // ── Variantes por rango de fechas arbitrario (usadas por el reporte de ventas) ──
    BigDecimal getTotalCommercialSalesAmountByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    Integer getTotalCommercialSalesByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    BigDecimal getTotalPlatformComissionsByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsByDateRangePage(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);

    /** Listado transaccional día a día (ventas individuales) del panel de ventas. */
    PagedResponse<DailySaleResponseDTO> getDailySalesPage(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);
}
