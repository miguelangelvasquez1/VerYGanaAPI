package com.verygana2.dtos.wompi;

import lombok.Builder;
import lombok.Data;

/**
 * Parámetros necesarios para construir la URL del Checkout de Wompi.
 *
 * Con el flujo de Checkout NO se hace una llamada HTTP al backend de Wompi
 * para crear la transacción. En cambio, el backend genera una URL con los
 * parámetros firmados y el frontend redirige al usuario a esa URL.
 *
 * Wompi construye la URL así:
 * https://checkout.wompi.co/p/?public-key=...&currency=...&amount-in-cents=...
 *   &reference=...&signature:integrity=...&redirect-url=...
 */
@Data
@Builder
public class WompiCheckoutRequestDTO {

    /**
     * Referencia única de tu sistema para este copago.
     * Wompi la devuelve en el webhook para identificar el Copayment.
     * Formato recomendado: "VG-COP-{uuid-corto}" (máx 50 caracteres).
     */
    private String reference;

    /**
     * Monto en centavos de COP que el usuario pagará en el checkout.
     * Solo la parte en dinero real del copago (cashAmountCents).
     * La parte en llaves NO aparece aquí — es una operación interna.
     */
    private Long amountInCents;

    /**
     * Email del usuario. Wompi lo pre-llena en el formulario de pago
     * para agilizar el proceso.
     */
    private String customerEmail;

    /**
     * URL a la que Wompi redirige al usuario después de completar o
     * cancelar el pago. Tu frontend debe manejar esta URL para mostrar
     * el resultado al usuario mientras espera el webhook.
     *
     * Ejemplo: "https://app.verygana.com/compra/resultado"
     */
    private String redirectUrl;
}
