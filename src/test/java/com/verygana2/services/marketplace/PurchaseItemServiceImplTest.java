package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import com.verygana2.dtos.PagedResponse;
import com.verygana2.dtos.product.responses.FeaturedProductResponseDTO;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemServiceImpl")
class PurchaseItemServiceImplTest {

    @Mock PurchaseItemRepository purchaseItemRepository;

    @InjectMocks PurchaseItemServiceImpl service;

    // ─── getTotalSalesbyCommercial ────────────────────────────────────────────

    @Nested
    @DisplayName("getTotalSalesbyCommercial")
    class GetTotalSalesByCommercial {

        @Test
        @DisplayName("throws IllegalArgumentException for null commercial ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getTotalSalesbyCommercial(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for zero commercial ID")
        void throwsOnZeroId() {
            assertThatThrownBy(() -> service.getTotalSalesbyCommercial(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for negative commercial ID")
        void throwsOnNegativeId() {
            assertThatThrownBy(() -> service.getTotalSalesbyCommercial(-1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegates to repository and returns count")
        void delegatesToRepository() {
            when(purchaseItemRepository.countTotalSalesByCommercialId(5L)).thenReturn(42L);

            Long result = service.getTotalSalesbyCommercial(5L);

            assertThat(result).isEqualTo(42L);
            verify(purchaseItemRepository).countTotalSalesByCommercialId(5L);
        }
    }

    // ─── getDeliveredItemsWithoutReview ───────────────────────────────────────

    @Nested
    @DisplayName("getDeliveredItemsWithoutReview")
    class GetDeliveredItemsWithoutReview {

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getDeliveredItemsWithoutReview(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for non-positive consumer ID")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.getDeliveredItemsWithoutReview(0L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegates to repository and returns list")
        void delegatesToRepository() {
            PurchaseItem item = new PurchaseItem();
            when(purchaseItemRepository.findDeliveredItemsWithoutReview(3L)).thenReturn(List.of(item));

            List<PurchaseItem> result = service.getDeliveredItemsWithoutReview(3L);

            assertThat(result).containsExactly(item);
        }
    }

    // ─── canUserReviewPurchaseItem ────────────────────────────────────────────

    @Nested
    @DisplayName("canUserReviewPurchaseItem")
    class CanUserReviewPurchaseItem {

        @Test
        @DisplayName("throws IllegalArgumentException for null purchaseItem ID")
        void throwsOnNullPurchaseItemId() {
            assertThatThrownBy(() -> service.canUserReviewPurchaseItem(null, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullConsumerId() {
            assertThatThrownBy(() -> service.canUserReviewPurchaseItem(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegates to repository and returns result")
        void delegatesToRepository() {
            when(purchaseItemRepository.canUserReviewPurchaseItem(10L, 2L)).thenReturn(true);

            boolean result = service.canUserReviewPurchaseItem(10L, 2L);

            assertThat(result).isTrue();
        }
    }

    // ─── getByIdAndConsumerId ─────────────────────────────────────────────────

    @Nested
    @DisplayName("getByIdAndConsumerId")
    class GetByIdAndConsumerId {

        @Test
        @DisplayName("throws IllegalArgumentException for null purchaseItem ID")
        void throwsOnNullPurchaseItemId() {
            assertThatThrownBy(() -> service.getByIdAndConsumerId(null, 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for null consumer ID")
        void throwsOnNullConsumerId() {
            assertThatThrownBy(() -> service.getByIdAndConsumerId(1L, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("returns PurchaseItem when found")
        void returnsPurchaseItemWhenFound() {
            PurchaseItem item = new PurchaseItem();
            when(purchaseItemRepository.findByIdAndConsumerId(7L, 3L)).thenReturn(Optional.of(item));

            PurchaseItem result = service.getByIdAndConsumerId(7L, 3L);

            assertThat(result).isSameAs(item);
        }

        @Test
        @DisplayName("throws when PurchaseItem not found")
        void throwsWhenNotFound() {
            when(purchaseItemRepository.findByIdAndConsumerId(99L, 1L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getByIdAndConsumerId(99L, 1L))
                    .isInstanceOf(Exception.class);
        }
    }

    // ─── monthly stats ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getTotalCommercialSalesAmountByMonth")
    class GetTotalCommercialSalesAmountByMonth {

        @Test
        @DisplayName("throws for null commercial ID")
        void throwsOnNullCommercialId() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(null, 2025, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for null year")
        void throwsOnNullYear() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(1L, null, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for null month")
        void throwsOnNullMonth() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(1L, 2025, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for month 0")
        void throwsOnMonthZero() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(1L, 2025, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for month 13")
        void throwsOnMonthThirteen() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(1L, 2025, 13))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("delegates to repository with correct date range")
        void delegatesToRepository() {
            when(purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(any(), any(), any()))
                    .thenReturn(BigDecimal.valueOf(500000));

            BigDecimal result = service.getTotalCommercialSalesAmountByMonth(1L, 2025, 3);

            assertThat(result).isEqualByComparingTo(BigDecimal.valueOf(500000));
        }
    }

    // ─── getTopSellingProductsPage ────────────────────────────────────────────

    @Nested
    @DisplayName("getTopSellingProductsPage")
    class GetTopSellingProductsPage {

        @Test
        @DisplayName("throws for null commercial ID")
        void throwsOnNullId() {
            assertThatThrownBy(() -> service.getTopSellingProductsPage(null, PageRequest.of(0, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("throws for non-positive commercial ID")
        void throwsOnNonPositiveId() {
            assertThatThrownBy(() -> service.getTopSellingProductsPage(0L, PageRequest.of(0, 10)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("prepends CDN domain to imageUrl and returns paged response")
        void prependsDomainToImageUrl() {
            Pageable pageable = PageRequest.of(0, 10);
            FeaturedProductResponseDTO dto = new FeaturedProductResponseDTO(1L, "Prod", "img.jpg", 10000L, 4.5, 100L);
            Page<FeaturedProductResponseDTO> page = new PageImpl<>(List.of(dto));

            when(purchaseItemRepository.findTopSellingProducts(1L, pageable)).thenReturn(page);

            PagedResponse<FeaturedProductResponseDTO> result = service.getTopSellingProductsPage(1L, pageable);

            assertThat(result.getData()).hasSize(1);
            assertThat(result.getData().get(0).getImageUrl()).startsWith("https://cdn.verygana.com/public/");
        }
    }
}
