package com.verygana2.schedulers;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.services.interfaces.marketplace.ProductService;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link ProductCleanupScheduler}: purga en lote los productos
 * REJECTED/INACTIVE elegibles; que una purga falle no detiene a las demás.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductCleanupScheduler")
class ProductCleanupSchedulerTest {

    @Mock private ProductService productService;

    private ProductCleanupScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ProductCleanupScheduler(productService);
        ReflectionTestUtils.setField(scheduler, "gracePeriodDays", 30);
    }

    @Test
    @DisplayName("sin productos elegibles: no intenta purgar nada")
    void noCandidates_doesNothing() {
        when(productService.findPurgeableProductIds(30)).thenReturn(List.of());

        scheduler.purgeInactiveAndRejectedProducts();

        verify(productService, never()).purgeProduct(anyLong());
    }

    @Test
    @DisplayName("varios candidatos: purga cada uno")
    void multipleCandidates_purgesEach() {
        when(productService.findPurgeableProductIds(30)).thenReturn(List.of(1L, 2L, 3L));

        scheduler.purgeInactiveAndRejectedProducts();

        verify(productService).purgeProduct(1L);
        verify(productService).purgeProduct(2L);
        verify(productService).purgeProduct(3L);
    }

    @Test
    @DisplayName("una purga falla: se captura el error y se sigue con los demás productos")
    void onePurgeFails_continuesWithOthers() {
        when(productService.findPurgeableProductIds(anyInt())).thenReturn(List.of(1L, 2L, 3L));
        doThrow(new IllegalStateException("boom")).when(productService).purgeProduct(2L);

        scheduler.purgeInactiveAndRejectedProducts();

        verify(productService).purgeProduct(1L);
        verify(productService).purgeProduct(2L);
        verify(productService).purgeProduct(3L);
    }

    @Test
    @DisplayName("usa el gracePeriodDays inyectado por configuración, no un valor fijo")
    void usesConfiguredGracePeriod() {
        ReflectionTestUtils.setField(scheduler, "gracePeriodDays", 45);
        when(productService.findPurgeableProductIds(45)).thenReturn(List.of());

        scheduler.purgeInactiveAndRejectedProducts();

        verify(productService).findPurgeableProductIds(45);
    }
}
