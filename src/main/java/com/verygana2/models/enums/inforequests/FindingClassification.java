package com.verygana2.models.enums.inforequests;

/**
 * Naturaleza del hecho. No hay un indicador único de "fraude": un error aislado, la falta de
 * actualización y una falsedad verificada son cosas distintas con tratamientos distintos.
 */
public enum FindingClassification {
    SUBSANABLE_ERROR,
    LACK_OF_UPDATE,
    ISOLATED_INCONSISTENCY,
    VERIFIED_FALSE_INFO,
    EXPRESS_REFUSAL
}
