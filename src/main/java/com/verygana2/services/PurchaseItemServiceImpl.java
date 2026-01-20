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
    public Long getTotalSalesbySeller(Long sellerId) {
        if (sellerId == null) {
            throw new IllegalArgumentException("Seller id cannot be null");
        }

        if (sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }
        return purchaseItemRepository.countTotalSalesBySellerId(sellerId);
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
    public BigDecimal getTotalSellerSalesAmountByMonth(Long sellerId, Integer year, Integer month) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }

        if (year == null || year <= 0) {
            throw new IllegalArgumentException("The year must be positive");
        }

        if (month == null || month <= 0 || month > 12) {
            throw new IllegalArgumentException("The month number must be between 1 and 12");
        }

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return purchaseItemRepository.sumTotalSellerSalesAmountByMonth(sellerId, startDate, endDate);
    }

    @Override
    public Integer getTotalSellerSalesByMonth(Long sellerId, Integer year, Integer month) {

        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }

        if (year == null || year <= 0) {
            throw new IllegalArgumentException("The year must be positive");
        }

        if (month == null || month <= 0 || month > 12) {
            throw new IllegalArgumentException("The month number must be between 1 and 12");
        }

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return purchaseItemRepository.findTotalSellerSalesByMonth(sellerId, startDate, endDate);
    }

    @Override
    public BigDecimal getTotalPlatformComissionsByMonth(Long sellerId, Integer year, Integer month) {

        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }

        if (year == null || year <= 0) {
            throw new IllegalArgumentException("The year must be positive");
        }

        if (month == null || month <= 0 || month > 12) {
            throw new IllegalArgumentException("The month number must be between 1 and 12");
        }

        ZonedDateTime startDate = ZonedDateTime.of(year, month, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota"));
        ZonedDateTime endDate = startDate.plusMonths(1);
        return purchaseItemRepository.sumTotalPlatformCommissionsByMonth(sellerId, startDate, endDate);

    }

    @Override
    public PagedResponse<FeaturedProductResponseDTO> getTopSellingProductsPage(Long sellerId, Pageable pageable) {
        
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("Seller id must be positive");
        }

        Page<FeaturedProductResponseDTO> topSellingProducts = purchaseItemRepository.findTopSellingProducts(sellerId, pageable);
        
        return PagedResponse.from(topSellingProducts);
    }

}
