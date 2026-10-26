package com.verygana2.services.marketplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.marketplace.ComputableSaleStatus;
import com.verygana2.models.enums.marketplace.SaleComponentType;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.marketplace.SaleComponent;
import com.verygana2.repositories.marketplace.SaleComponentRepository;

/**
 * Venta Computable (Contrato B 5.9): el precio reconocido es la suma de sus componentes,
 * sin computar solo el dinero del usuario y sin reconocer importes que no existen.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SaleComponentRecorder")
class SaleComponentRecorderTest {

    @Mock private SaleComponentRepository repository;

    private SaleComponentRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new SaleComponentRecorder(repository);
    }

    private static Purchase purchaseWith(PurchaseItem item) {
        Purchase purchase = Purchase.builder().id(1L).build();
        purchase.addItem(item);
        return purchase;
    }

    private static PurchaseItem item(long subtotal, Long keys) {
        return PurchaseItem.builder().id(7L).subtotalCents(subtotal).keysValueCents(keys).build();
    }

    @Test
    @DisplayName("compra ordinaria (PDF 5.9 F): $60.000 de dinero + $40.000 de llaves = Venta Computable de $100.000")
    @SuppressWarnings("unchecked")
    void consumerSaleCountsCashAndKeys() {
        PurchaseItem item = item(10_000_000L, 4_000_000L);

        recorder.recognizeConsumerSale(purchaseWith(item), UUID.randomUUID());

        ArgumentCaptor<List<SaleComponent>> saved = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(SaleComponent::getComponentType, SaleComponent::getAmountCents)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(SaleComponentType.USER_CASH, 6_000_000L),
                        org.assertj.core.groups.Tuple.tuple(SaleComponentType.KEYS_BACKING, 4_000_000L));
        assertThat(item.getComputableStatus()).isEqualTo(ComputableSaleStatus.RECOGNIZED);
        assertThat(item.getRecognizedAt()).isNotNull();
    }

    @Test
    @DisplayName("un ítem ya reconocido no registra otra venta (entrega o reintento)")
    void alreadyRecognizedIsIdempotent() {
        PurchaseItem item = item(10_000_000L, 0L);
        item.setComputableStatus(ComputableSaleStatus.RECOGNIZED);

        recorder.recognizeInstitutionalSale(purchaseWith(item), UUID.randomUUID());

        verify(repository, never()).saveAll(anyIterable());
    }

    @Test
    @DisplayName("componentes que no suman el precio: la venta queda pendiente y no se completa con un importe inexistente")
    void mismatchKeepsSalePending() {
        // Llaves por encima del precio: el "dinero" quedaría negativo y no hay forma de cuadrar.
        PurchaseItem item = item(10_000_000L, 12_000_000L);

        assertThatThrownBy(() -> recorder.recognizeConsumerSale(purchaseWith(item), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("queda pendiente");
        verify(repository, never()).saveAll(anyIterable());
        assertThat(item.getComputableStatus()).isEqualTo(ComputableSaleStatus.PENDING);
    }

    @Test
    @DisplayName("una venta reversada no se vuelve a reconocer")
    void reversedCannotBeRecognizedAgain() {
        PurchaseItem item = item(10_000_000L, 0L);
        item.setComputableStatus(ComputableSaleStatus.REVERSED);

        assertThatThrownBy(() -> recorder.recognizeInstitutionalSale(purchaseWith(item), UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).save(any());
    }
}
