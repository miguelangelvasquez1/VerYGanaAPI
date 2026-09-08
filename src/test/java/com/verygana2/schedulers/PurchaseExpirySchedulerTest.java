package com.verygana2.schedulers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.services.interfaces.marketplace.CopaymentService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests de {@link PurchaseExpiryScheduler}: es un disparador delgado que delega
 * en {@link CopaymentService#expireStale(int)} con la ventana configurada.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseExpiryScheduler")
class PurchaseExpirySchedulerTest {

    @Mock private CopaymentService copaymentService;

    private PurchaseExpiryScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PurchaseExpiryScheduler(copaymentService);
        ReflectionTestUtils.setField(scheduler, "maxAgeMinutes", 30);
    }

    @Test
    @DisplayName("delega en copaymentService.expireStale con el maxAgeMinutes por defecto (30)")
    void delegatesToExpireStaleWithDefaultWindow() {
        scheduler.expireStale();

        verify(copaymentService).expireStale(30);
        verifyNoMoreInteractions(copaymentService);
    }

    @Test
    @DisplayName("respeta el maxAgeMinutes inyectado por configuración")
    void usesConfiguredWindow() {
        ReflectionTestUtils.setField(scheduler, "maxAgeMinutes", 45);

        scheduler.expireStale();

        verify(copaymentService).expireStale(45);
    }
}
