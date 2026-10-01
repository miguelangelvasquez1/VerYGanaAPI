package com.verygana2.schedulers;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.services.interfaces.EmailService;
import com.verygana2.services.interfaces.marketplace.PurchaseItemRefundService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseItemExpirationScheduler}: procesa cada ítem físico
 * vencido de forma aislada (una falla no detiene a los demás ni salta las
 * notificaciones de los ítems que sí se procesaron bien).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemExpirationScheduler")
class PurchaseItemExpirationSchedulerTest {

    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private PurchaseItemRefundService purchaseItemRefundService;
    @Mock private EmailService emailService;

    private PurchaseItemExpirationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new PurchaseItemExpirationScheduler(purchaseItemRepository, purchaseItemRefundService, emailService);
    }

    private PurchaseItem item(Long id) {
        Purchase purchase = Purchase.builder().id(50L).deliveryEmail("comprador@test.com").build();
        PurchaseItem item = new PurchaseItem();
        item.setId(id);
        item.setPurchase(purchase);
        return item;
    }

    @Test
    @DisplayName("sin ítems vencidos: no hace nada")
    void noExpiredItems_doesNothing() {
        when(purchaseItemRepository.findExpiredUnclaimedPhysicalItems(any())).thenReturn(List.of());

        scheduler.expireUnclaimedPhysicalItems();

        verifyNoInteractions(purchaseItemRefundService, emailService);
    }

    @Test
    @DisplayName("ítem vencido: lo expira y notifica a comprador y comerciante")
    void expiredItem_expiresAndNotifiesBoth() {
        PurchaseItem item = item(1L);
        when(purchaseItemRepository.findExpiredUnclaimedPhysicalItems(any())).thenReturn(List.of(item));

        scheduler.expireUnclaimedPhysicalItems();

        verify(purchaseItemRefundService).expireUnclaimed(item);
        verify(emailService).sendPhysicalItemExpiredToConsumer(item, "comprador@test.com");
        verify(emailService).sendPhysicalItemExpiredToCommercial(item);
    }

    @Test
    @DisplayName("expireUnclaimed falla para un ítem: no notifica ese ítem, pero sigue con los demás")
    void expireFailsForOneItem_skipsNotificationsButContinuesWithOthers() {
        PurchaseItem failing = item(1L);
        PurchaseItem healthy = item(2L);
        when(purchaseItemRepository.findExpiredUnclaimedPhysicalItems(any())).thenReturn(List.of(failing, healthy));
        org.mockito.Mockito.doThrow(new IllegalStateException("boom"))
                .when(purchaseItemRefundService).expireUnclaimed(failing);

        scheduler.expireUnclaimedPhysicalItems();

        verify(emailService, never()).sendPhysicalItemExpiredToConsumer(org.mockito.ArgumentMatchers.eq(failing), any());
        verify(purchaseItemRefundService).expireUnclaimed(healthy);
        verify(emailService).sendPhysicalItemExpiredToConsumer(healthy, "comprador@test.com");
        verify(emailService).sendPhysicalItemExpiredToCommercial(healthy);
    }

    @Test
    @DisplayName("falla el correo al comprador: igual notifica al comerciante")
    void consumerEmailFails_stillNotifiesCommercial() {
        PurchaseItem item = item(1L);
        when(purchaseItemRepository.findExpiredUnclaimedPhysicalItems(any())).thenReturn(List.of(item));
        org.mockito.Mockito.doThrow(new RuntimeException("SMTP caído"))
                .when(emailService).sendPhysicalItemExpiredToConsumer(any(), any());

        scheduler.expireUnclaimedPhysicalItems();

        verify(emailService).sendPhysicalItemExpiredToCommercial(item);
    }
}
