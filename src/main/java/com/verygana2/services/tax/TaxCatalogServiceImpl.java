package com.verygana2.services.tax;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.verygana2.dtos.tax.TaxProfileDTOs.CatalogEntry;
import com.verygana2.dtos.tax.TaxRequestDTOs.CatalogEntryRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.RetireCatalogEntryRequest;
import com.verygana2.exceptions.InvalidRequestException;
import com.verygana2.models.enums.tax.TaxCatalogType;
import com.verygana2.models.tax.TaxCatalogEntry;
import com.verygana2.repositories.tax.TaxCatalogEntryRepository;
import com.verygana2.services.interfaces.tax.TaxCatalogService;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxCatalogServiceImpl implements TaxCatalogService {

    private static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    private final TaxCatalogEntryRepository catalogRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CatalogEntry> listAll() {
        LocalDate today = LocalDate.now(BOGOTA);
        return catalogRepository.findAllByOrderByCatalogTypeAscCodeAsc().stream()
                .map(e -> toDTO(e, today)).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CatalogEntry> listActive() {
        LocalDate today = LocalDate.now(BOGOTA);
        return catalogRepository.findAllByOrderByCatalogTypeAscCodeAsc().stream()
                .filter(e -> e.isActiveOn(today))
                .map(e -> toDTO(e, today)).toList();
    }

    @Override
    public CatalogEntry create(CatalogEntryRequest request, Long userId) {
        String code = request.getCode().trim();
        if (catalogRepository.existsByCatalogTypeAndCode(request.getCatalogType(), code)) {
            throw new InvalidRequestException("El código " + code + " ya existe en el catálogo "
                    + request.getCatalogType() + ". Un código retirado no se reutiliza.");
        }
        TaxCatalogEntry entry = new TaxCatalogEntry();
        entry.setCatalogType(request.getCatalogType());
        entry.setCode(code);
        entry.setLabel(request.getLabel().trim());
        entry.setLegalBasis(request.getLegalBasis().trim());
        entry.setValidFrom(request.getValidFrom());
        entry.setCreatedByUserId(userId);
        return toDTO(catalogRepository.save(entry), LocalDate.now(BOGOTA));
    }

    @Override
    public CatalogEntry retire(Long entryId, RetireCatalogEntryRequest request, Long userId) {
        TaxCatalogEntry entry = catalogRepository.findById(entryId)
                .orElseThrow(() -> new EntityNotFoundException("Código de catálogo no encontrado: " + entryId));
        if (entry.getValidTo() != null) {
            throw new InvalidRequestException("Este código ya fue retirado.");
        }
        if (request.getValidTo().isBefore(entry.getValidFrom())) {
            throw new InvalidRequestException("El fin de vigencia no puede ser anterior al inicio.");
        }
        entry.setValidTo(request.getValidTo());
        entry.setRetiredByUserId(userId);
        return toDTO(catalogRepository.save(entry), LocalDate.now(BOGOTA));
    }

    @Override
    @Transactional(readOnly = true)
    public void requireActiveCode(TaxCatalogType type, String code) {
        if (code == null) {
            return;
        }
        TaxCatalogEntry entry = catalogRepository.findByCatalogTypeAndCode(type, code)
                .orElseThrow(() -> new InvalidRequestException(
                        "El código " + code + " no existe en el catálogo " + type + "."));
        if (!entry.isActiveOn(LocalDate.now(BOGOTA))) {
            throw new InvalidRequestException("El código " + code + " del catálogo " + type + " no está vigente.");
        }
    }

    private static CatalogEntry toDTO(TaxCatalogEntry e, LocalDate today) {
        return new CatalogEntry(e.getId(), e.getCatalogType(), e.getCode(), e.getLabel(), e.getLegalBasis(),
                e.getValidFrom(), e.getValidTo(), e.isActiveOn(today));
    }
}
