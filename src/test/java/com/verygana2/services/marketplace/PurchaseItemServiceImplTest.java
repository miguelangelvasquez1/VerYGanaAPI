package com.verygana2.services.marketplace;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.exceptions.marketplaceExceptions.InvalidClaimException;
import com.verygana2.models.enums.marketplace.ProductType;
import com.verygana2.models.enums.marketplace.PurchaseItemStatus;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.security.ProductCodeEncryptor;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseItemServiceImpl}: consultas de ventas/comisiones por
 * comercial y rango de fechas arbitrario, y la validación de argumentos que se
 * repite en prácticamente todos sus métodos (ids positivos, rango de fechas válido).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemServiceImpl")
class PurchaseItemServiceImplTest {

    @Mock private PurchaseItemRepository purchaseItemRepository;
    @Mock private ProductCodeEncryptor codeEncryptor;
    @Mock private PasswordEncoder passwordEncoder;

    private PurchaseItemServiceImpl service;

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime END = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new PurchaseItemServiceImpl(purchaseItemRepository, codeEncryptor, passwordEncoder);
    }

    private PurchaseItem physicalPendingItem() {
        Product product = new Product();
        product.setProductType(ProductType.PHYSICAL);
        PurchaseItem item = new PurchaseItem();
        item.setProduct(product);
        item.setCommercialId(9L);
        item.setStatus(PurchaseItemStatus.PENDING);
        item.setClaimPinHash("hashed-pin");
        item.setClaimAttempts(0);
        item.setClaimExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).plusDays(1));
        return item;
    }

    @Nested
    @DisplayName("claimPhysicalItem")
    class ClaimPhysicalItem {

        @Test
        @DisplayName("PIN correcto: marca el ítem como CLAIMED")
        void correctPin_marksItemClaimed() {
            PurchaseItem item = physicalPendingItem();
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(passwordEncoder.matches("123456", "hashed-pin")).thenReturn(true);

            service.claimPhysicalItem(1L, 9L, "123456");

            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.CLAIMED);
            assertThat(item.getClaimedAt()).isNotNull();
            verify(purchaseItemRepository).save(item);
        }

        @Test
        @DisplayName("PIN incorrecto: incrementa los intentos y lanza InvalidClaimException sin reclamar")
        void wrongPin_incrementsAttemptsAndThrows() {
            PurchaseItem item = physicalPendingItem();
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));
            when(passwordEncoder.matches("000000", "hashed-pin")).thenReturn(false);

            assertThatThrownBy(() -> service.claimPhysicalItem(1L, 9L, "000000"))
                    .isInstanceOf(InvalidClaimException.class);

            assertThat(item.getStatus()).isEqualTo(PurchaseItemStatus.PENDING);
            assertThat(item.getClaimAttempts()).isEqualTo(1);
        }

        @Test
        @DisplayName("ítem que no pertenece al comercial autenticado: lanza InvalidClaimException")
        void notOwnedByCommercial_throwsInvalidClaimException() {
            PurchaseItem item = physicalPendingItem();
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.claimPhysicalItem(1L, 999L, "123456"))
                    .isInstanceOf(InvalidClaimException.class);
            verify(purchaseItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("ítem ya reclamado: idempotente, no reprocesa ni lanza error")
        void alreadyClaimed_isIdempotent() {
            PurchaseItem item = physicalPendingItem();
            item.setStatus(PurchaseItemStatus.CLAIMED);
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));

            service.claimPhysicalItem(1L, 9L, "123456");

            verify(purchaseItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("producto digital (no requiere PIN): lanza InvalidClaimException")
        void digitalProduct_throwsInvalidClaimException() {
            PurchaseItem item = physicalPendingItem();
            item.getProduct().setProductType(ProductType.DIGITAL);
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.claimPhysicalItem(1L, 9L, "123456"))
                    .isInstanceOf(InvalidClaimException.class);
        }

        @Test
        @DisplayName("plazo de reclamación vencido: lanza InvalidClaimException")
        void expiredClaimWindow_throwsInvalidClaimException() {
            PurchaseItem item = physicalPendingItem();
            item.setClaimExpiresAt(ZonedDateTime.now(ZoneOffset.UTC).minusDays(1));
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.claimPhysicalItem(1L, 9L, "123456"))
                    .isInstanceOf(InvalidClaimException.class);
        }

        @Test
        @DisplayName("intentos agotados (5): lanza InvalidClaimException sin volver a comparar el PIN")
        void maxAttemptsExceeded_throwsInvalidClaimException() {
            PurchaseItem item = physicalPendingItem();
            item.setClaimAttempts(5);
            when(purchaseItemRepository.findById(1L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.claimPhysicalItem(1L, 9L, "123456"))
                    .isInstanceOf(InvalidClaimException.class);
            org.mockito.Mockito.verifyNoInteractions(passwordEncoder);
        }
    }

    @Nested
    @DisplayName("getTotalCommercialSalesAmountByDateRange")
    class SalesAmountByDateRange {

        @Test
        @DisplayName("delega en el método default del repositorio (que ya convierte a pesos) con el rango recibido")
        void delegatesToRepositoryWithGivenRange() {
            // sumTotalCommercialSalesAmountByMonth es un método `default` de la interfaz del
            // repositorio: como el repositorio está mockeado, Mockito NO ejecuta su cuerpo real
            // (que llama a la variante ...Cents y convierte), así que hay que stubearlo
            // directamente devolviendo ya el BigDecimal esperado.
            when(purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(
                    org.mockito.ArgumentMatchers.eq(9L), any(), any())).thenReturn(BigDecimal.valueOf(15_000));

            BigDecimal result = service.getTotalCommercialSalesAmountByDateRange(9L, START, END);

            assertThat(result).isEqualByComparingTo("15000");
            org.mockito.Mockito.verify(purchaseItemRepository)
                    .sumTotalCommercialSalesAmountByMonth(9L, START, END);
        }

        @Test
        @DisplayName("sin ventas en el rango: el servicio retorna tal cual lo que responda el repositorio (BigDecimal.ZERO)")
        void noSales_passesThroughRepositoryZero() {
            when(purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(any(), any(), any()))
                    .thenReturn(BigDecimal.ZERO);

            assertThat(service.getTotalCommercialSalesAmountByDateRange(9L, START, END)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("startDate no anterior a endDate: lanza IllegalArgumentException")
        void startNotBeforeEnd_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByDateRange(9L, END, START))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("commercialId inválido (<=0): lanza IllegalArgumentException")
        void invalidCommercialId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByDateRange(0L, START, END))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("startDate o endDate null: lanza IllegalArgumentException")
        void nullDates_throwIllegalArgumentException() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByDateRange(9L, null, END))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByDateRange(9L, START, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getTotalPlatformComissionsByDateRange: delega en el método default del repositorio")
    void getTotalPlatformComissionsByDateRange_delegatesToRepository() {
        when(purchaseItemRepository.sumTotalPlatformCommissionsByMonth(
                org.mockito.ArgumentMatchers.eq(9L), any(), any())).thenReturn(BigDecimal.valueOf(2_500));

        assertThat(service.getTotalPlatformComissionsByDateRange(9L, START, END)).isEqualByComparingTo("2500");
    }

    @Test
    @DisplayName("getByIdAndConsumerId: item que no pertenece al consumidor lanza ObjectNotFoundException")
    void getByIdAndConsumerId_notOwned_throwsObjectNotFoundException() {
        when(purchaseItemRepository.findByIdAndConsumerId(1L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIdAndConsumerId(1L, 2L)).isInstanceOf(ObjectNotFoundException.class);
    }

    @Test
    @DisplayName("getByIdAndConsumerId: id inválido lanza IllegalArgumentException antes de consultar el repositorio")
    void getByIdAndConsumerId_invalidId_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> service.getByIdAndConsumerId(0L, 2L)).isInstanceOf(IllegalArgumentException.class);
        org.mockito.Mockito.verifyNoInteractions(purchaseItemRepository);
    }

    @Test
    @DisplayName("getDeliveredItemsWithoutReview: delega en el repositorio")
    void getDeliveredItemsWithoutReview_delegatesToRepository() {
        PurchaseItem item = new PurchaseItem();
        when(purchaseItemRepository.findDeliveredItemsWithoutReview(9L)).thenReturn(List.of(item));

        assertThat(service.getDeliveredItemsWithoutReview(9L)).containsExactly(item);
    }

    @Nested
    @DisplayName("getDeliveredCode")
    class GetDeliveredCode {

        @Test
        @DisplayName("item del consumidor con código entregado: lo desencripta y lo retorna")
        void ownedAndDelivered_returnsDecryptedCode() {
            PurchaseItem item = new PurchaseItem();
            item.setDeliveredCode("cipherText123");
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));
            when(codeEncryptor.decrypt("cipherText123")).thenReturn("PLAINCODE-1234");

            assertThat(service.getDeliveredCode(1L, 9L)).isEqualTo("PLAINCODE-1234");
        }

        @Test
        @DisplayName("item que no pertenece al consumidor (o no existe): lanza EntityNotFoundException sin desencriptar nada")
        void notOwned_throwsEntityNotFoundException() {
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getDeliveredCode(1L, 9L))
                    .isInstanceOf(EntityNotFoundException.class);
            org.mockito.Mockito.verifyNoInteractions(codeEncryptor);
        }

        @Test
        @DisplayName("item aún no entregado (deliveredCode null): lanza InvalidStatusException sin desencriptar nada")
        void notYetDelivered_throwsInvalidStatusException() {
            PurchaseItem item = new PurchaseItem();
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.getDeliveredCode(1L, 9L))
                    .isInstanceOf(InvalidStatusException.class);
            org.mockito.Mockito.verifyNoInteractions(codeEncryptor);
        }

        @Test
        @DisplayName("purchaseItemId inválido (<=0): lanza IllegalArgumentException antes de consultar el repositorio")
        void invalidPurchaseItemId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getDeliveredCode(0L, 9L))
                    .isInstanceOf(IllegalArgumentException.class);
            org.mockito.Mockito.verifyNoInteractions(purchaseItemRepository);
        }

        @Test
        @DisplayName("consumerId inválido (<=0): lanza IllegalArgumentException antes de consultar el repositorio")
        void invalidConsumerId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getDeliveredCode(1L, 0L))
                    .isInstanceOf(IllegalArgumentException.class);
            org.mockito.Mockito.verifyNoInteractions(purchaseItemRepository);
        }
    }

    @Nested
    @DisplayName("getReportableItem")
    class GetReportableItem {

        @Test
        @DisplayName("ítem del consumidor en estado reportable: lo retorna")
        void ownedAndReportable_returnsItem() {
            PurchaseItem item = new PurchaseItem();
            item.setStatus(PurchaseItemStatus.PENDING);
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThat(service.getReportableItem(1L, 9L)).isSameAs(item);
        }

        @Test
        @DisplayName("ítem ya REFUNDED: lanza InvalidStatusException")
        void refundedItem_throwsInvalidStatusException() {
            PurchaseItem item = new PurchaseItem();
            item.setStatus(PurchaseItemStatus.REFUNDED);
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.getReportableItem(1L, 9L))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("ítem CANCELLED: lanza InvalidStatusException")
        void cancelledItem_throwsInvalidStatusException() {
            PurchaseItem item = new PurchaseItem();
            item.setStatus(PurchaseItemStatus.CANCELLED);
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.getReportableItem(1L, 9L))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("ítem IN_REVIEW (ya hay un PQRS abierto sobre él): lanza InvalidStatusException")
        void inReviewItem_throwsInvalidStatusException() {
            PurchaseItem item = new PurchaseItem();
            item.setStatus(PurchaseItemStatus.IN_REVIEW);
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.getReportableItem(1L, 9L))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("ítem CLAIMED dentro de la ventana de 48h: lo retorna")
        void claimedWithinWindow_returnsItem() {
            PurchaseItem item = new PurchaseItem();
            item.setStatus(PurchaseItemStatus.CLAIMED);
            item.setClaimedAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(10));
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThat(service.getReportableItem(1L, 9L)).isSameAs(item);
        }

        @Test
        @DisplayName("ítem CLAIMED fuera de la ventana de 48h: lanza InvalidStatusException")
        void claimedPastWindow_throwsInvalidStatusException() {
            PurchaseItem item = new PurchaseItem();
            item.setStatus(PurchaseItemStatus.CLAIMED);
            item.setClaimedAt(ZonedDateTime.now(ZoneOffset.UTC).minusHours(49));
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> service.getReportableItem(1L, 9L))
                    .isInstanceOf(InvalidStatusException.class);
        }

        @Test
        @DisplayName("ítem que no pertenece al consumidor (o no existe): lanza ObjectNotFoundException")
        void notOwned_throwsObjectNotFoundException() {
            when(purchaseItemRepository.findByIdAndConsumerId(1L, 9L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getReportableItem(1L, 9L))
                    .isInstanceOf(ObjectNotFoundException.class);
        }
    }
}
