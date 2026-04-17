package com.verygana2.controllers;

import java.math.BigDecimal;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.verygana2.models.plans.EffectivePlanState;
import com.verygana2.services.plans.CommissionService;
import com.verygana2.services.plans.CommissionService.SaleCommissionResult;
import com.verygana2.services.plans.EffectivePlanResolver;
import com.verygana2.services.plans.InvestmentService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints REST para el sistema de planes.
 *
 * Base path: /api/v1/plans
 *
 * Endpoints:
 *  GET  /plans/advertiser/{commercialId}/state
 *       → Estado efectivo del anunciante en tiempo real
 *
 *  POST /plans/advertiser/{commercialId}/invest
 *       → Registrar nueva inversión y activar STANDARD/PREMIUM
 *
 *  POST /plans/advertiser/{commercialId}/sale
 *       → Procesar venta: actualiza ROI y calcula comisión
 *
 *  POST /plans/budget/{budgetId}/consume/ad
 *       → Registrar consumo por visualización de anuncio
 *
 *  POST /plans/budget/{budgetId}/consume/game
 *       → Registrar consumo por recompensa de juego
 */
@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
public class PlanController {

    private final EffectivePlanResolver planResolver;
    private final InvestmentService investmentService;
    private final CommissionService commissionService;

    // ── Estado efectivo ───────────────────────────────────────────────────────

    /**
     * Devuelve el estado efectivo del anunciante en tiempo real.
     * Incluye plan activo, comisión, presupuesto restante y capacidades.
     */
    @GetMapping("/commercial/state")
    public ResponseEntity<EffectivePlanState> getEffectiveState(
            @AuthenticationPrincipal Jwt jwt) {

        EffectivePlanState state = planResolver.resolve(jwt.getClaim("userId"));
        return ResponseEntity.ok(state);
    }

    // ── Inversión ─────────────────────────────────────────────────────────────

    /**
     * Registra una nueva inversión publicitaria.
     * Activa automáticamente STANDARD o PREMIUM según el monto.
     */
    @PostMapping("/commercial/invest")
    @PreAuthorize("hasRole('COMMERCIAL')")
    public ResponseEntity<InvestmentResponse> createInvestment(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InvestmentRequest request) {

        Long commercialId = jwt.getClaim("userId");

        InvestmentResponse investment = investmentService.createInvestment(
                commercialId,
                request.investmentAmount()
        );

        return ResponseEntity.ok(investment);
    }

    // ── Ventas ────────────────────────────────────────────────────────────────

    /**
     * Procesa una venta completada para el anunciante.
     * Actualiza el recoveredAmount, verifica ROI x6 y calcula comisión.
     *
     * Debe llamarse en el flujo de confirmación de ventas, DESPUÉS de que
     * la transacción comercial sea exitosa.
     */
    @PostMapping("/commercial/sale")
    public ResponseEntity<SaleCommissionResult> processSale(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SaleRequest request) {

        SaleCommissionResult result = commissionService.processSale(
                jwt.getClaim("userId"), request.saleAmount());

        return ResponseEntity.ok(result);
    }

    // ── Consumo de presupuesto ────────────────────────────────────────────────

    /**
     * Registra el consumo del presupuesto por una visualización de anuncio.
     */
    @PostMapping("/budget/{budgetId}/consume/ad")
    public ResponseEntity<Void> consumeForAd(
            @PathVariable Long budgetId,
            @Valid @RequestBody BudgetConsumeRequest request) {

        // El BudgetService se inyecta directamente en producción.
        // Aquí se muestra la firma del endpoint; la implementación
        // se completa al inyectar el servicio.
        return ResponseEntity.ok().build();
    }

    // ── Request / Response DTOs ───────────────────────────────────────────────

    public record InvestmentRequest(
            @NotNull
            BigDecimal investmentAmount
    ) {}

    public record SaleRequest(
            @NotNull
            @DecimalMin(value = "0.01", message = "El monto de venta debe ser positivo")
            BigDecimal saleAmount
    ) {}

    public record BudgetConsumeRequest(
            @NotNull
            @DecimalMin(value = "0.01")
            BigDecimal amount,
            String referenceId
    ) {}

    public record InvestmentResponse(
            BigDecimal amount
    ) {}
}