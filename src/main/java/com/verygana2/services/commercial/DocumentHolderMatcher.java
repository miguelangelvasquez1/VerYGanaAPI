package com.verygana2.services.commercial;

import com.verygana2.models.enums.commercial.CommercialDocumentType;
import com.verygana2.models.userDetails.CommercialDetails;

/**
 * Comprueba que un documento corresponda al empresario (control "RUT de otro titular").
 * Compara solo dígitos: "900.123.456-7" y "9001234567" son el mismo NIT.
 */
final class DocumentHolderMatcher {

    private DocumentHolderMatcher() {
    }

    /** True si el tipo de documento identifica a un titular que se puede comprobar. */
    static boolean requiresHolder(CommercialDocumentType type) {
        return expectedKind(type) != null;
    }

    static boolean matches(CommercialDocumentType type, String holderIdentification, CommercialDetails commercial) {
        HolderKind kind = expectedKind(type);
        if (kind == null) {
            return true;
        }
        String holder = digits(holderIdentification);
        if (holder.isEmpty()) {
            return false;
        }
        return switch (kind) {
            case COMPANY -> matchesNit(holder, digits(commercial.getNit()));
            case LEGAL_REPRESENTATIVE -> holder.equals(digits(commercial.getLegalRepDocNumber()));
        };
    }

    /**
     * El NIT puede venir con o sin dígito de verificación: el del documento puede tener un
     * dígito más que el registrado (o al revés).
     */
    private static boolean matchesNit(String holder, String nit) {
        if (nit.isEmpty()) {
            return false;
        }
        if (holder.equals(nit)) {
            return true;
        }
        return (holder.length() == nit.length() + 1 && holder.startsWith(nit))
                || (nit.length() == holder.length() + 1 && nit.startsWith(holder));
    }

    private static HolderKind expectedKind(CommercialDocumentType type) {
        return switch (type) {
            case RUT, CAMARA_COMERCIO, CERTIFICACION_BANCARIA -> HolderKind.COMPANY;
            case CEDULA_REPRESENTANTE -> HolderKind.LEGAL_REPRESENTATIVE;
            case PERMISO_SECTORIAL, MARCA_REGISTRADA, OTRO -> null;
        };
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private enum HolderKind { COMPANY, LEGAL_REPRESENTATIVE }
}
