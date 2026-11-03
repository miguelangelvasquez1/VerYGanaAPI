package com.verygana2.services.interfaces.tax;

import java.util.List;

import com.verygana2.dtos.tax.TaxProfileDTOs.Establishment;
import com.verygana2.dtos.tax.TaxRequestDTOs.CloseEstablishmentRequest;
import com.verygana2.dtos.tax.TaxRequestDTOs.EstablishmentRequest;

/** Establecimientos físicos del empresario (inciso f del 4.7). */
public interface CommercialEstablishmentService {

    /** Todos, activos y cerrados, más recientes primero. */
    List<Establishment> list(Long commercialId);

    List<Establishment> listActive(Long commercialId);

    int countActive(Long commercialId);

    Establishment create(Long commercialId, EstablishmentRequest request);

    Establishment close(Long commercialId, Long establishmentId, CloseEstablishmentRequest request);
}
