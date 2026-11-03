package com.verygana2.repositories.tax;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.verygana2.models.enums.tax.TaxCatalogType;
import com.verygana2.models.tax.TaxCatalogEntry;

public interface TaxCatalogEntryRepository extends JpaRepository<TaxCatalogEntry, Long> {

    Optional<TaxCatalogEntry> findByCatalogTypeAndCode(TaxCatalogType catalogType, String code);

    boolean existsByCatalogTypeAndCode(TaxCatalogType catalogType, String code);

    List<TaxCatalogEntry> findAllByOrderByCatalogTypeAscCodeAsc();
}
