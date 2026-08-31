package com.verygana2.models.enums.commercial;

/**
 * Modalidad de vinculación asignada al comerciante tras el diagnóstico comercial.
 * El cuestionario del "Insumo técnico de caracterización empresarial" produce A, B
 * o C; la ruta alternativa de integración técnica (techIntegrationNeeds) produce D;
 * E queda reservada para la negociación especial que se solicita en acceptPlan().
 */
public enum CommercialRoute {
    A, // Empresa Tipo A — vende directamente, operación concentrada, hasta 3 ofertas → plan BASIC
    B, // Empresa Tipo B — vende directamente con mayor capacidad, campañas y juegos → plan STANDARD
    C, // Candidata a Empresa Premium — ecosistema institucional con red de empresarios independientes → plan PREMIUM
    D, // Proveedor o aliado de servicios — requiere integración técnica, condiciones coordinadas con un asesor
    E  // Piloto, fundador o alianza especial — requiere negociación corporativa o aprobación previa especial
}
