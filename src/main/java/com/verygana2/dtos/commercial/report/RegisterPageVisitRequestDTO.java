package com.verygana2.dtos.commercial.report;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code POST /commercials/page-visits}: registra un clic del consumer
 * sobre el enlace del anuncio que redirige a la página oficial del empresario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterPageVisitRequestDTO {

    @NotNull(message = "adId es obligatorio")
    private Long adId;

    /** URL efectivamente abierta. Opcional — si viene null se usa la del anuncio. */
    private String targetUrl;
}
