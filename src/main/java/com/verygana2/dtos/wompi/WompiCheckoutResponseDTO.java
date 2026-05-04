package com.verygana2.dtos.wompi;

import lombok.Builder;
import lombok.Data;

/**
 * Respuesta del backend al frontend cuando se inicia un checkout de Wompi.
 * El frontend usa checkoutUrl para redirigir al usuario.
 */
@Data
@Builder
public class WompiCheckoutResponseDTO {

    /**
     * URL completa del checkout de Wompi lista para redirigir.
     * Ejemplo:
     * https://checkout.wompi.co/p/?public-key=pub_stagtest_xxx
     *   &currency=COP&amount-in-cents=1000000&reference=VG-COP-abc123
     *   &signature:integrity=sha256hash&redirect-url=https://app.verygana.com/...
     */
    private String checkoutUrl;

    /**
     * Referencia del copago. El frontend la guarda para poder
     * consultar el estado mientras espera el webhook o la redirección.
     */
    private String reference;

    /**
     * Monto en centavos que se va a cobrar con Wompi.
     * El frontend puede mostrarlo al usuario como confirmación.
     */
    private Long amountInCents;
}