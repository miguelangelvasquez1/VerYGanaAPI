package com.verygana2.services.marketplace;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.hibernate.ObjectNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.repositories.marketplace.PurchaseItemRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests de {@link PurchaseItemServiceImpl}: consultas de ventas/comisiones
 * por comercial y mes, y la validación de argumentos que se repite en
 * prácticamente todos sus métodos (ids positivos, mes entre 1 y 12).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PurchaseItemServiceImpl")
class PurchaseItemServiceImplTest {

    @Mock private PurchaseItemRepository purchaseItemRepository;

    private PurchaseItemServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PurchaseItemServiceImpl(purchaseItemRepository);
    }

    @Nested
    @DisplayName("getTotalCommercialSalesAmountByMonth")
    class SalesAmountByMonth {

        @Test
        @DisplayName("delega en el método default del repositorio (que ya convierte a pesos) con el rango correcto del mes en zona Colombia")
        void delegatesToRepositoryWithCorrectMonthRange() {
            // sumTotalCommercialSalesAmountByMonth es un método `default` de la interfaz del
            // repositorio: como el repositorio está mockeado, Mockito NO ejecuta su cuerpo real
            // (que llama a la variante ...Cents y convierte), así que hay que stubearlo
            // directamente devolviendo ya el BigDecimal esperado.
            when(purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(
                    org.mockito.ArgumentMatchers.eq(9L), any(), any())).thenReturn(BigDecimal.valueOf(15_000));

            BigDecimal result = service.getTotalCommercialSalesAmountByMonth(9L, 2026, 3);

            assertThat(result).isEqualByComparingTo("15000");

            ArgumentCaptor<ZonedDateTime> startCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            ArgumentCaptor<ZonedDateTime> endCaptor = ArgumentCaptor.forClass(ZonedDateTime.class);
            org.mockito.Mockito.verify(purchaseItemRepository).sumTotalCommercialSalesAmountByMonth(
                    org.mockito.ArgumentMatchers.eq(9L), startCaptor.capture(), endCaptor.capture());
            assertThat(startCaptor.getValue()).isEqualTo(
                    ZonedDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota")));
            assertThat(endCaptor.getValue()).isEqualTo(
                    ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneId.of("America/Bogota")));
        }

        @Test
        @DisplayName("sin ventas ese mes: el servicio retorna tal cual lo que responda el repositorio (BigDecimal.ZERO)")
        void noSales_passesThroughRepositoryZero() {
            when(purchaseItemRepository.sumTotalCommercialSalesAmountByMonth(any(), any(), any()))
                    .thenReturn(BigDecimal.ZERO);

            assertThat(service.getTotalCommercialSalesAmountByMonth(9L, 2026, 3)).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("mes fuera de rango (13): lanza IllegalArgumentException")
        void invalidMonth_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(9L, 2026, 13))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("commercialId inválido (<=0): lanza IllegalArgumentException")
        void invalidCommercialId_throwsIllegalArgumentException() {
            assertThatThrownBy(() -> service.getTotalCommercialSalesAmountByMonth(0L, 2026, 3))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getTotalPlatformComissionsByMonth: delega en el método default del repositorio")
    void getTotalPlatformComissionsByMonth_delegatesToRepository() {
        when(purchaseItemRepository.sumTotalPlatformCommissionsByMonth(
                org.mockito.ArgumentMatchers.eq(9L), any(), any())).thenReturn(BigDecimal.valueOf(2_500));

        assertThat(service.getTotalPlatformComissionsByMonth(9L, 2026, 3)).isEqualByComparingTo("2500");
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
}
