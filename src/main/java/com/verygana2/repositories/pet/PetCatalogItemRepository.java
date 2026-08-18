package com.verygana2.repositories.pet;



import com.verygana2.models.pets.PetCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PetCatalogItemRepository extends JpaRepository<PetCatalogItem, Long> {
    List<PetCatalogItem> findAllByActiveTrue();

    /**
     * Usado por /spend para cobrar el precio del servidor en vez del que manda el
     * cliente. Se busca por externalId porque es el id que ve el juego (ver
     * PetCatalogServiceImpl.getAllCatalogItems), no la PK.
     *
     * A propósito NO filtra por active: `active` controla si el ítem se le ofrece
     * al juego en /pet/catalog, no si tiene precio. Los ítems horneados en el build
     * se guardan con active=false —el juego ya los tiene, exponerlos los duplicaría—
     * pero igual necesitan precio cuando alguien los compra.
     */
    Optional<PetCatalogItem> findByExternalId(Integer externalId);
}