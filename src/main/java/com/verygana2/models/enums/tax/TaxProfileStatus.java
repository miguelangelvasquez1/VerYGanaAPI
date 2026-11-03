package com.verygana2.models.enums.tax;

/** Estado de revisión de la versión vigente de la ficha tributaria. */
public enum TaxProfileStatus {
    /** Enviada por el empresario; falta revisar al menos una sección. */
    SUBMITTED,
    /** Alguna sección tiene observaciones que el empresario debe aclarar. */
    OBSERVED,
    /** Todas las secciones revisadas sin observaciones. */
    REVIEWED
}
