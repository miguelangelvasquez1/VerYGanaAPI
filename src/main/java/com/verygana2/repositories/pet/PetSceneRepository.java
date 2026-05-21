package com.verygana2.repositories.pet;

import com.verygana2.models.pets.PetScene;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PetSceneRepository extends JpaRepository<PetScene, Long> {
    List<PetScene> findAllByActiveTrue();
}