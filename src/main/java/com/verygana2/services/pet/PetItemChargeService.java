package com.verygana2.services.pet;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.repositories.pet.CatalogIntegrationRequestRepository;
import com.verygana2.repositories.pet.PetCatalogItemRepository;
import com.verygana2.services.interfaces.finance.TreasuryService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cobro por uso de los ítems que un comercial integró al juego de mascotas.
 *
 * Ciclo del dinero, igual que el de las campañas de juegos:
 * wallet del comercial → bolsa reservada al enviar la solicitud
 * ({@code BudgetService#consumeForPetItemRequest}) → un cobro por cada unidad que un
 * consumidor compra → OPERATIONS. Si la solicitud se rechaza, la bolsa vuelve entera
 * a la wallet.
 *
 * El evento que cobra es la compra, no el uso dentro del juego: Unity no le avisa al
 * backend cuando se le da el ítem a la mascota, y la compra es lo único que vemos.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetItemChargeService {

    @Value("${pets.commercial-charge-per-use-cents:15000}")
    private long chargePerUseCents;

    private final CatalogIntegrationRequestRepository requestRepository;
    private final PetCatalogItemRepository catalogItemRepository;
    private final TreasuryService treasuryService;

    /**
     * Cobra a la bolsa del comercial las unidades compradas de su ítem. No hace nada si el
     * ítem no salió de una solicitud (los horneados en el build) o si la solicitud es
     * anterior al cobro y no reservó bolsa.
     *
     * Al agotarse la bolsa el ítem se desactiva: deja de listarse en el catálogo del juego.
     * La compra que la agota se cobra recortada a lo que quedaba, nunca sobregira.
     *
     * @param referenceId id de la KeyTransaction de la compra
     * @return centavos cobrados a la bolsa
     */
    @Transactional
    public long chargeForPurchase(PetCatalogItem item, long quantity, UUID referenceId) {
        if (item == null || item.getId() == null || quantity <= 0) {
            return 0L;
        }

        CatalogIntegrationRequest request = requestRepository
                .findByResultCatalogItemIdForUpdate(item.getId()).orElse(null);
        if (request == null || request.getBudgetCents() == null) {
            return 0L;
        }

        long charged = request.chargeUse(chargePerUseCents * quantity);
        if (charged > 0) {
            requestRepository.save(request);
            treasuryService.registerPetItemCharge(charged, referenceId);
        }

        if (request.getRemainingBudgetCents() == 0L && Boolean.TRUE.equals(item.getActive())) {
            item.setActive(false);
            catalogItemRepository.save(item);
            log.info("Bolsa de la solicitud {} agotada: el ítem {} se desactiva del catálogo",
                    request.getId(), item.getId());
        }

        log.debug("Cobro por uso del ítem {}: {} ¢ (bolsa {}/{})",
                item.getId(), charged, request.getSpentCents(), request.getBudgetCents());
        return charged;
    }

    public long getChargePerUseCents() {
        return chargePerUseCents;
    }
}
