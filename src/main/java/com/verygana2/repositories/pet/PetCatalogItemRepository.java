package com.verygana2.repositories.pet;



import com.verygana2.models.pets.PetCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /**
     * Vía de respaldo para la ropa de la tienda, que no manda id: el juego envía el
     * nombre interno del accesorio ("monoculo", "goku", "ninja") en {@code itemName}
     * y {@code resolveCatalogId()} devuelve null al no poder convertirlo a número.
     *
     * Sin esto, cada prenda se cobra al precio que diga el cliente.
     *
     * Ignora mayúsculas porque el nombre lo teclea una persona en el panel y el que
     * manda el juego viene fijado en el build; que un acento o una mayúscula deje de
     * cobrar el precio correcto sería difícil de detectar.
     */
    Optional<PetCatalogItem> findByNameIgnoreCase(String name);

    /**
     * Siguiente externalId libre para un ítem creado desde el panel.
     *
     * Arranca en 1000 y solo mira de ahí hacia arriba: por debajo viven los ids del
     * catálogo horneado en el build (0–14), y reutilizar uno haría que una compra de
     * comida del juego cobrara el precio de un ítem nuestro.
     *
     * Antes lo tecleaba el diseñador, sin nada que impidiera repetir un número.
     */
    @Query("SELECT COALESCE(MAX(i.externalId), 999) + 1 FROM PetCatalogItem i WHERE i.externalId >= 1000")
    Integer nextExternalId();
}