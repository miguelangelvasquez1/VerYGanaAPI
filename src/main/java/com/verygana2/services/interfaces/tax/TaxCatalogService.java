package com.verygana2.services.interfaces.tax;

import java.util.List;

import com.verygana2.dtos.tax.TaxProfileDTOs.CatalogEntry;
import com.verygana2.dtos.tax.TaxRequestDTOs.CatalogEntryRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.RetireCatalogEntryRequest;
import com.verygana2.models.enums.tax.TaxCatalogType;

/** Catálogo de códigos tributarios autorizados, con fundamento y vigencia. */
public interface TaxCatalogService {

    List<CatalogEntry> listAll();

    /** Solo los códigos vigentes hoy: lo que el empresario puede elegir en la ficha. */
    List<CatalogEntry> listActive();

    CatalogEntry create(CatalogEntryRequest request, Long userId);

    CatalogEntry retire(Long entryId, RetireCatalogEntryRequest request, Long userId);

    /** Lanza si el código no existe en el catálogo o no está vigente hoy. Null se acepta. */
    void requireActiveCode(TaxCatalogType type, String code);
}
