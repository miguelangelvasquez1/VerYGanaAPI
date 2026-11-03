package com.verygana2.repositories.declarations;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.declarations.DeclarationManifestation;
import com.verygana2.models.enums.declarations.DeclarationCode;
import com.verygana2.models.enums.declarations.ManifestationStatus;

public interface DeclarationManifestationRepository extends JpaRepository<DeclarationManifestation, Long> {

    Optional<DeclarationManifestation> findByIdempotencyKey(String idempotencyKey);

    /** Manifestación expresa ya registrada en este contrato para esta versión del texto. */
    Optional<DeclarationManifestation> findFirstByContract_IdAndCodeAndDeclarationText_IdAndStatus(
            Long contractId, DeclarationCode code, Long declarationTextId, ManifestationStatus status);

    List<DeclarationManifestation> findByCommercial_IdAndCodeOrderByOccurredAtDescIdDesc(
            Long commercialId, DeclarationCode code);

    boolean existsByCommercial_IdAndStatus(Long commercialId, ManifestationStatus status);
}
