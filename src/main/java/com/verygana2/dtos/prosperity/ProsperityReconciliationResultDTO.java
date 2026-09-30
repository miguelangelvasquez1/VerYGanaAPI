package com.verygana2.dtos.prosperity;

import java.util.List;

/** Resultado de la conciliación libro vs. cuentas de prosperidad. Vacío = sin descuadres. */
public record ProsperityReconciliationResultDTO(int accountsChecked, List<String> discrepancies) {
}
