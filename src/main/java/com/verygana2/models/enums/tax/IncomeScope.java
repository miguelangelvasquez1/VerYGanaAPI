package com.verygana2.models.enums.tax;

/**
 * Qué operaciones incluye un valor informado. Existe para no sumar dos veces las ventas de
 * VERYGANA: un total CONSOLIDATED ya las incluye.
 */
public enum IncomeScope {
    EXTERNAL_ONLY,
    PLATFORM_ONLY,
    CONSOLIDATED_INCLUDES_PLATFORM
}
