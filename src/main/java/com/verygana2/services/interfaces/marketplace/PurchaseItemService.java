package com.verygana2.services.interfaces.marketplace;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.models.marketplace.PurchaseItem;

public interface PurchaseItemService {
    Long getTotalSalesbyCommercial (Long commercialId);
    List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId);
    boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId);
    PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId);
    PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long commercialId, Pageable pageable);
    String getDeliveredCode (Long purchaseItemId, Long consumerId);

    // ── Variantes por rango de fechas arbitrario (usadas por el reporte de ventas) ──
    BigDecimal getTotalCommercialSalesAmountByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    Integer getTotalCommercialSalesByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    BigDecimal getTotalPlatformComissionsByDateRange(Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate);
    PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsByDateRangePage(
            Long commercialId, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable);
}
