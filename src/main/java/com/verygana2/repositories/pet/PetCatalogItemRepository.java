package com.verygana2.repositories.pet;



import com.verygana2.models.pets.PetCatalogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PetCatalogItemRepository extends JpaRepository<PetCatalogItem, Long> {
    List<PetCatalogItem> findAllByActiveTrue();
}