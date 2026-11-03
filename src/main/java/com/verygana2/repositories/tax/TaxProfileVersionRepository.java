package com.verygana2.repositories.tax;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.tax.TaxProfileVersion;

public interface TaxProfileVersionRepository extends JpaRepository<TaxProfileVersion, Long> {

    Optional<TaxProfileVersion> findByTaxProfile_IdAndVersionNumber(Long taxProfileId, int versionNumber);

    List<TaxProfileVersion> findByTaxProfile_IdOrderByVersionNumberDesc(Long taxProfileId);
}
