package com.verygana2.models.enums.recognitions;

/**
 * Estado de un reconocimiento. Va aparte del estado de la compra y del de cada ítem
 * (Contrato B 10.16 A): pagar la compra no la deja entregada.
 */
public enum RecognitionStatus {
    /** Comprado y pagado; el beneficiario aún no recibe todo lo comprado. */
    PURCHASED,
    /** Todos los ítems fueron entregados (digital) o reclamados con PIN (físico). */
    DELIVERED,
    /** Un ítem físico venció sin reclamarse: el dinero volvió al fondo. */
    EXPIRED,
    /** Anulado por un admin: el dinero volvió al fondo. */
    CANCELLED
}
