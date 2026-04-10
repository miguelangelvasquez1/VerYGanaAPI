package com.verygana2.services;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.hibernate.ObjectNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.models.products.PurchaseItem;
import com.verygana2.repositories.PurchaseItemRepository;
import com.verygana2.services.interfaces.PurchaseItemService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PurchaseItemServiceImpl implements PurchaseItemService {

    private final PurchaseItemRepository purchaseItemRepository;

    @Override
    public Long getTotalSalesbyCommercial(Long commercialId) {
        if (commercialId == null) {
            throw new IllegalArgumentException("Commercial id cannot be null");
        }

        if (commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }
        return purchaseItemRepository.countTotalSalesByCommercialId(commercialId);
    }

    @Override
    public List<PurchaseItem> getDeliveredItemsWithoutReview(Long consumerId) {
        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.findDeliveredItemsWithoutReview(consumerId);
    }

    @Override
    public boolean canUserReviewPurchaseItem(Long purchaseItemId, Long consumerId) {
        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("purchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.canUserReviewPurchaseItem(purchaseItemId, consumerId);
    }

    @Override
    public PurchaseItem getByIdAndConsumerId(Long purchaseItemId, Long consumerId) {

        if (purchaseItemId == null || purchaseItemId <= 0) {
            throw new IllegalArgumentException("PurchaseItem id must be positive");
        }

        if (consumerId == null || consumerId <= 0) {
            throw new IllegalArgumentException("Consumer id must be positive");
        }

        return purchaseItemRepository.findByIdAndConsumerId(purchaseItemId, consumerId)
                .orElseThrow(() -> new ObjectNotFoundException("Purchase item with id:" + purchaseItemId + " not found",
                        PurchaseItem.class));
    }

    @Override
    public BigDecimal getTotalCommercialSalesAmountByMonth(Long commercialId, Integer year, Integer month) {
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        if (year == null || year <= 0) {
            throw new IllegalArgumentException("The year must be positive");
        }

        if (month == null || month <= 0 || month > 12) {
            throw new IllegalArgumentException("The month number must be between 1 and 12");
        }

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(commercialId, startDate, endDate);
    }

    @Override
    public Integer getTotalCommercialSalesByMonth(Long commercialId, Integer year, Integer month) {

        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        if (year == null || year <= 0) {
            throw new IllegalArgumentException("The year must be positive");
        }

        if (month == null || month <= 0 || month > 12) {
            throw new IllegalArgumentException("The month number must be between 1 and 12");
        }

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return purchaseItemRepository.findTotalCommercialSalesByMonth(commercialId, startDate, endDate);
    }

    @Override
    public BigDecimal getTotalPlatformComissionsByMonth(Long commercialId, Integer year, Integer month) {

        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        if (year == null || year <= 0) {
            throw new IllegalArgumentException("The year must be positive");
        }

        if (month == null || month <= 0 || month > 12) {
            throw new IllegalArgumentException("The month number must be between 1 and 12");
        }

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return purchaseItemRepository.sumTotalPlatformCommissionsByMonth(commercialId, startDate, endDate);

    }

    @Override
    public PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long commercialId, Pageable pageable) {
        
        if (commercialId == null || commercialId <= 0) {
            throw new IllegalArgumentException("Commercial id must be positive");
        }

        Page<FeaturedProductResponseDTO> topSellingProducts = purchaseItemRepository.findTopSellingProducts(commercialId, pageable);
        
        return PagedResponse.from(topSellingProducts);
    }

}
