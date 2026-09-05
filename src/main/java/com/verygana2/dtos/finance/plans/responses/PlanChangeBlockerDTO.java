package com.verygana2.dtos.finance.plans.responses;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un tipo de activo que el comercial tiene por encima de lo que permitiría el plan
 * destino — el cambio de plan no puede solicitarse hasta que lo ajuste.
 *
 * Cubre tanto el caso "el plan destino permite menos" (p.ej. PREMIUM→STANDARD baja
 * el máximo de anuncios) como "el plan destino no permite este activo" (p.ej.
 * STANDARD→PREMIUM y PREMIUM no vende productos, o cualquier plan→BASIC sin
 * anuncios/juegos/encuestas). En el segundo caso {@code allowedByTargetPlan} es 0.
 *
 * <p>Los activos no se borran directamente: el comercial debe esperar a que finalicen,
 * o contactar al soporte de VerYGana para cancelarlos antes de tiempo.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanChangeBlockerDTO {

    /** Tipo de activo: {@code PRODUCTS}, {@code ADS}, {@code BRANDED_GAMES}, {@code SURVEYS}. */
    private String assetType;

    /** Etiqueta legible en español (plural) lista para interpolar en un mensaje, p.ej. "anuncios". */
    private String assetLabel;

    /** Cuántos activos de este tipo tiene el comercial ocupando un cupo del plan ahora mismo. */
    private long currentCount;

    /** Cuántos permite el plan destino (0 = el plan destino no admite este activo). */
    private int allowedByTargetPlan;

    /**
     * Cuántos sobran respecto al plan destino ({@code currentCount - allowedByTargetPlan}):
     * es el número que debe dejar de estar activo (finalizando o vía soporte) para poder
     * solicitar el cambio.
     */
    private long excessCount;

    /** Mensaje explicativo en lenguaje natural, listo para mostrar. */
    private String message;
}
