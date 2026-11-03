package com.verygana2.repositories.declarations;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.declarations.DeclarationText;
import com.verygana2.models.enums.declarations.DeclarationCode;

public interface DeclarationTextRepository extends JpaRepository<DeclarationText, Long> {

    Optional<DeclarationText> findFirstByCodeAndActiveTrueOrderByPublishedAtDesc(DeclarationCode code);
}
