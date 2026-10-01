package com.verygana2.models.enums.pqrs;

public enum PqrsAssetStatus {
    PENDING,    // pre-signed URL solicitada, archivo aún no subido/confirmado
    VALIDATED,  // archivo subido y validado (tamaño + mime real); puede o no estar ya reclamado por un Pqrs
    ORPHANED,   // nunca se confirmó o nunca se reclamó; candidato a limpieza
    DELETED     // eliminado de R2
}
