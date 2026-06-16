package com.verygana2.repositories.pet;


import com.verygana2.models.pets.PetSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PetSessionRepository extends JpaRepository<PetSession, Long> {
    Optional<PetSession> findBySessionToken(String sessionToken);
}