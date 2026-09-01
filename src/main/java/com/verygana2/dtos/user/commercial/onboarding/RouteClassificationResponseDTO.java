package com.verygana2.dtos.user.commercial.onboarding;

import com.verygana2.models.enums.commercial.CommercialRoute;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RouteClassificationResponseDTO {
    private CommercialRoute route;
    private String routeLabel;

    /** Nombre de la modalidad para el empresario: "Empresa Tipo A" / "Empresa Tipo B" / "Candidata a Empresa Premium". */
    private String modalityLabel;

    private String explanation;

    /**
     * true cuando la recomendación es aproximada (faltan señales o el perfil está
     * en el borde entre modalidades). El empresario continúa por el flujo normal.
     */
    private boolean preliminary;

    /**
     * true para "candidata a Empresa Premium": el front debe mostrar que el
     * resultado está sujeto a documentos, verificación y aprobación. No bloquea el flujo.
     */
    private boolean verificationRequired;

    private boolean confirmed;
}
