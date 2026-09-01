package com.verygana2.services.interfaces.commercial;

import com.verygana2.dtos.user.commercial.responses.CommercialDashboardSummaryResponseDTO;
import com.verygana2.models.enums.commercial.DashboardPeriod;

/**
 * Arma, en una sola llamada, el payload del panel de inicio del comercial
 * ({@code GET /commercial/dashboard/summary}) agregando lo que ya calculan
 * los servicios de ventas, planes, anuncios, encuestas, juegos y aliados.
 */
public interface CommercialDashboardService {

    CommercialDashboardSummaryResponseDTO getSummary(Long commercialId, DashboardPeriod period);
}
