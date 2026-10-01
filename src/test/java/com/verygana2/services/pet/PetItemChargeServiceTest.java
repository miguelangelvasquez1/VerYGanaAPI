package com.verygana2.services.pet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.repositories.pet.CatalogIntegrationRequestRepository;
import com.verygana2.repositories.pet.PetCatalogItemRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

/**
 * Cobro por uso de los ítems de mascotas: la bolsa que reservó el comercial baja $150 por
 * unidad comprada, el cobro pasa a OPERATIONS y el ítem sale del catálogo al agotarse.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PetItemChargeService")
class PetItemChargeServiceTest {

    private static final long CHARGE_PER_USE = 15_000L;

    @Mock private CatalogIntegrationRequestRepository requestRepository;
    @Mock private PetCatalogItemRepository catalogItemRepository;
    @Mock private TreasuryService treasuryService;

    private PetItemChargeService service;
    private PetCatalogItem item;
    private final UUID purchaseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PetItemChargeService(requestRepository, catalogItemRepository, treasuryService);
        ReflectionTestUtils.setField(service, "chargePerUseCents", CHARGE_PER_USE);

        item = new PetCatalogItem();
        item.setId(5L);
        item.setActive(true);
    }

    private CatalogIntegrationRequest requestWithBudget(Long budgetCents, long spentCents) {
        CatalogIntegrationRequest request = new CatalogIntegrationRequest();
        request.setId(7L);
        request.setBudgetCents(budgetCents);
        request.setSpentCents(spentCents);
        when(requestRepository.findByResultCatalogItemIdForUpdate(5L)).thenReturn(Optional.of(request));
        return request;
    }

    @Test
    @DisplayName("cobra $150 por unidad a la bolsa y los pasa a OPERATIONS con la compra como referencia")
    void chargesPerUnit() {
        CatalogIntegrationRequest request = requestWithBudget(10 * CHARGE_PER_USE, 0L);

        long charged = service.chargeForPurchase(item, 2L, purchaseId);

        assertThat(charged).isEqualTo(2 * CHARGE_PER_USE);
        assertThat(request.getSpentCents()).isEqualTo(2 * CHARGE_PER_USE);
        verify(requestRepository).save(request);
        verify(treasuryService).registerPetItemCharge(2 * CHARGE_PER_USE, purchaseId);
        assertThat(item.getActive()).isTrue();
    }

    @Test
    @DisplayName("la compra que agota la bolsa se recorta a lo que queda y el ítem sale del catálogo")
    void lastPurchaseIsClippedAndDeactivatesTheItem() {
        CatalogIntegrationRequest request = requestWithBudget(10 * CHARGE_PER_USE, 10 * CHARGE_PER_USE - 5_000L);

        long charged = service.chargeForPurchase(item, 1L, purchaseId);

        assertThat(charged).isEqualTo(5_000L);
        assertThat(request.getRemainingBudgetCents()).isZero();
        verify(treasuryService).registerPetItemCharge(5_000L, purchaseId);
        assertThat(item.getActive()).isFalse();
        verify(catalogItemRepository).save(item);
    }

    @Test
    @DisplayName("bolsa ya agotada: no cobra ni mueve tesorería")
    void exhaustedBudgetChargesNothing() {
        item.setActive(false);
        requestWithBudget(CHARGE_PER_USE, CHARGE_PER_USE);

        long charged = service.chargeForPurchase(item, 1L, purchaseId);

        assertThat(charged).isZero();
        verify(treasuryService, never()).registerPetItemCharge(anyLong(), any());
        verify(requestRepository, never()).save(any());
        verify(catalogItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("ítem horneado en el build (sin solicitud): no hay a quién cobrarle")
    void bakedItemHasNoCommercial() {
        when(requestRepository.findByResultCatalogItemIdForUpdate(5L)).thenReturn(Optional.empty());

        assertThat(service.chargeForPurchase(item, 1L, purchaseId)).isZero();
        verify(treasuryService, never()).registerPetItemCharge(anyLong(), any());
    }

    @Test
    @DisplayName("solicitud anterior al cobro (sin bolsa): el ítem sigue activo y no cobra")
    void legacyRequestWithoutBudget() {
        requestWithBudget(null, 0L);

        assertThat(service.chargeForPurchase(item, 1L, purchaseId)).isZero();
        verify(treasuryService, never()).registerPetItemCharge(anyLong(), any());
        assertThat(item.getActive()).isTrue();
    }

    @Test
    @DisplayName("compra de un ítem que no está en el catálogo: no busca solicitud")
    void unknownItem() {
        assertThat(service.chargeForPurchase(null, 1L, purchaseId)).isZero();
        verify(requestRepository, never()).findByResultCatalogItemIdForUpdate(eq(5L));
    }
}
