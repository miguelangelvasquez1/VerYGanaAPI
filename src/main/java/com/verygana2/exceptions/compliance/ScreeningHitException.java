package com.verygana2.exceptions.compliance;

import com.verygana2.models.enums.ScreeningList;
import com.verygana2.models.enums.ScreeningStatus;

public class ScreeningHitException extends RuntimeException {

    private final ScreeningList lista;
    private final ScreeningStatus status;

    public ScreeningHitException(ScreeningList lista, ScreeningStatus status, String nombreConsultado) {
        super("Screening " + status + " en lista " + lista + " para: " + nombreConsultado);
        this.lista = lista;
        this.status = status;
    }

    public ScreeningList getLista() { return lista; }
    public ScreeningStatus getStatus() { return status; }
}