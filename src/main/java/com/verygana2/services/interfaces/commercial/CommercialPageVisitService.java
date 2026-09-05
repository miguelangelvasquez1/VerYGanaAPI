package com.verygana2.services.interfaces.commercial;

import com.verygana2.dtos.commercial.report.RegisterPageVisitRequestDTO;

/**
 * Registro de visitas a la página oficial del empresario: cada vez que un
 * consumer hace clic en el enlace de un anuncio que redirige al sitio del
 * comercial ({@code POST /commercials/page-visits}).
 *
 * El almacenamiento no está gateado por plan (es una acción del consumer); el
 * gating aplica solo a la lectura agregada (ver {@link CommercialReportService}).
 */
public interface CommercialPageVisitService {

    /**
     * Registra la visita. Idempotente dentro de una ventana corta configurable
     * por (anuncio, consumer) para no inflar la métrica con doble clic o reintentos.
     */
    void registerVisit(Long consumerId, RegisterPageVisitRequestDTO request);
}
