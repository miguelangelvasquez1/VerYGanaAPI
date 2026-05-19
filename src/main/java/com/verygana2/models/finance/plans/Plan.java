package com.verygana2.models.finance.plans;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Define la estructura y capacidades de cada nivel de plan.
 *
 * DISEÑO HÍBRIDO:
 * - saleCommissionPct y monthlyPriceCents son campos directos porque se
 *   consultan en CADA transacción financiera — un join extra por venta
 *   sería costoso con alto volumen.
 * - El resto de capacidades (límites, booleanos, boosts) viven en
 *   PlanFeature para permitir configuración dinámica sin migraciones.
 *
 * BASIC    → suscripción mensual fija
 * STANDARD → depósito entre $1.000.000 y $9.999.999
 * PREMIUM  → depósito >= $10.000.000
 */
@Entity
@Table(
    name = "plans",
    uniqueConstraints = @UniqueConstraint(columnNames = { "version", "code" })
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Integer version;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, unique = true, length = 20)
    private PlanCode code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ─── Precio (campo directo — crítico para el checkout) ────────────────────

    /**
     * Precio mensual en centavos de COP. Solo aplica para BASIC.
     * Ej: $200.000 COP = 20_000_000 centavos.
     */
    @Column(name = "monthly_price_cents")
    private Long monthlyPriceCents;

    /**
     * Inversión mínima en centavos. BASIC → null.
     * STANDARD → 100_000_000 ($1.000.000 COP).
     * PREMIUM  → 1_000_000_000 ($10.000.000 COP).
     */
    @Column(name = "min_investment_cents")
    private Long minInvestmentCents;

    /**
     * Inversión máxima en centavos. STANDARD → 999_999_900. PREMIUM → null.
     */
    @Column(name = "max_investment_cents")
    private Long maxInvestmentCents;

    // ─── Comisión (campo directo — se consulta en CADA venta) ────────────────

    /**
     * Porcentaje de comisión por venta que retiene VeryGana.
     * Se aplica SIEMPRE sobre cada venta sin excepción.
     *
     * Campo directo (no en PlanFeature) porque PurchaseService lo consulta
     * en cada transacción. Un join adicional por venta sería ineficiente.
     * Modificable por el admin vía endpoint dedicado.
     *
     * Valores iniciales:
     *   BASIC    → 15
     *   STANDARD → 10
     *   PREMIUM  → 5
     */
    @Column(name = "sale_commission_pct", nullable = false)
    private int saleCommissionPct;

    /* Porcentaje maximo de llaves que pueden usar los usuarios al realizar una compra
    de un producto de un empresario (20%, 35% o 50%)
     */
    @Column(name = "max_keys_pct", nullable = false)
    private int maxKeysPct;

    // ─── Capacidades dinámicas (vía PlanFeature) ──────────────────────────────

    /**
     * Capacidades configurables dinámicamente por el admin.
     * Incluye: MAX_PRODUCTS, MAX_ADS, MAX_BRANDED_GAMES, CAN_ADVERTISE,
     *          CAN_USE_GAMES, VISIBILITY_BOOST, CAN_USE_SURVEYS, MAX_SURVEYS, etc.
     *
     * Para leer un feature:
     *   plan.getFeatureValue("MAX_PRODUCTS")
     *       .map(PlanFeature::getIntOrDefault(10))
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<PlanFeature> features = new ArrayList<>();

    // ─── Métodos de negocio ───────────────────────────────────────────────────

    public boolean isMonthlySubscription() {
        return code == PlanCode.BASIC;
    }

    /**
     * Busca el valor de una feature por su código.
     * Uso: plan.getFeatureValue("CAN_ADVERTISE")
     *          .map(pf -> pf.getBoolOrDefault(false))
     *          .orElse(false)
     */
    public Optional<PlanFeature> getFeatureValue(String featureCode) {
        return features.stream()
                .filter(pf -> pf.getFeature().getCode().equals(featureCode))
                .findFirst();
    }

    /**
     * Shortcut para leer un feature booleano.
     */
    public boolean getBoolFeature(String code, boolean defaultValue) {
        return getFeatureValue(code)
                .map(pf -> pf.getBoolOrDefault(defaultValue))
                .orElse(defaultValue);
    }

    /**
     * Shortcut para leer un feature de límite entero.
     * -1 significa ilimitado por convención.
     */
    public int getIntFeature(String code, int defaultValue) {
        return getFeatureValue(code)
                .map(pf -> pf.getIntOrDefault(defaultValue))
                .orElse(defaultValue);
    }

    public enum PlanCode {
        BASIC,
        STANDARD,
        PREMIUM
    }
}