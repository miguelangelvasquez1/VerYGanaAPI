package com.verygana2.repositories.tax;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.enums.tax.EstablishmentStatus;
import com.verygana2.models.tax.CommercialEstablishment;

public interface CommercialEstablishmentRepository extends JpaRepository<CommercialEstablishment, Long> {

    List<CommercialEstablishment> findByCommercial_IdOrderByCreatedAtDesc(Long commercialId);

    List<CommercialEstablishment> findByCommercial_IdAndStatus(Long commercialId, EstablishmentStatus status);

    int countByCommercial_IdAndStatus(Long commercialId, EstablishmentStatus status);

    boolean existsByCommercial_IdAndStatusAndAddressNormalizedAndMunicipality_Code(
            Long commercialId, EstablishmentStatus status, String addressNormalized, String municipalityCode);

    Optional<CommercialEstablishment> findByIdAndCommercial_Id(Long id, Long commercialId);
}
