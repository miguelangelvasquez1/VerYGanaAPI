package com.verygana2.repositories.tax;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.enums.tax.TaxProfileStatus;
import com.verygana2.models.tax.TaxProfile;

public interface TaxProfileRepository extends JpaRepository<TaxProfile, Long> {

    Optional<TaxProfile> findByCommercial_Id(Long commercialId);

    boolean existsByCommercial_Id(Long commercialId);

    Page<TaxProfile> findByStatus(TaxProfileStatus status, Pageable pageable);
}
