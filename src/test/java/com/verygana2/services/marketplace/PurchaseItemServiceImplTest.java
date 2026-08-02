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

import com.verygana2.exceptions.InvalidStatusException;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;
import com.verygana2.security.ProductCodeEncryptor;

import jakarta.persistence.EntityNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    private PurchaseItemServiceImpl service;

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    private static final ZonedDateTime END = ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new PurchaseItemServiceImpl(purchaseItemRepository, codeEncryptor);
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
}
