package com.verygana2.models.enums.inforequests;

/** Medida contractual decidida en un hallazgo. Nunca se aplica desde una alerta o un requerimiento. */
public enum ContractualMeasure {
    NONE,
    WARNING,
    PREVENTIVE_RESTRICTION,
    SUSPENSION,
    /** Se remite al procedimiento de terminación; no cambia el estado de la cuenta por sí sola. */
    TERMINATION_REFERRAL
}
