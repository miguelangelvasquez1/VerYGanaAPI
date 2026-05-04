package com.verygana2.models.userDetails;

import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Plan;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "commercial_details")
@Data
@EqualsAndHashCode(callSuper = false)
public class CommercialDetails extends UserDetails {

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, unique = true, length = 20)
    private String nit;

    // ===== MÉTODOS DE PAGO =====

    /**
     * Todos los métodos de pago registrados por este empresario.
     * Puede tener varios (cuenta Bancolombia, Nequi, etc.) igual que Airbnb
     * permite múltiples métodos de cobro.
     */
    @OneToMany(mappedBy = "commercial", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PayoutMethod> payoutMethods = new ArrayList<>();

    /**
     * Método de pago seleccionado como predeterminado.
     * El PayoutScheduler usa este método para ejecutar la transferencia diaria.
     *
     * POR QUÉ @OneToOne hacia PayoutMethod en lugar de un flag isDefault en PayoutMethod:
     * El enfoque de flag (isDefault = true) requiere que cada vez que se cambie el
     * default, se actualicen dos filas: la anterior (isDefault = false) y la nueva
     * (isDefault = true). Esto requiere una transacción extra y puede generar
     * inconsistencias si hay un fallo entre las dos escrituras.
     * Con un puntero directo aquí, cambiar el método por defecto es actualizar
     * UNA sola columna en commercial_details. Atómico y simple.
     *
     * NULLABLE: un comercial recién registrado aún no tiene método configurado.
     * El sistema debe verificar canReceivePayouts() antes de intentar un payout.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_payout_method_id")
    private PayoutMethod defaultPayoutMethod;

    // ===== WALLET =====

    @OneToOne(mappedBy = "commercial", fetch = FetchType.LAZY)
    private Wallet wallet;

    // ===== PLAN =====

    /**
     * Plan activo del empresario. Se actualiza en cada depósito según el saldo
     * total resultante. No se degrada automáticamente durante el consumo de presupuesto;
     * solo se recalcula cuando llega un nuevo depósito.
     * Nullable: un comercial BASIC sin inversión puede tener plan asignado directamente.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_plan_id")
    private Plan currentPlan;

    // ===== MÉTODOS DE NEGOCIO =====

    /**
     * El empresario puede recibir payouts solo si tiene un método por defecto
     * configurado, verificado y activo.
     * El PayoutScheduler llama esto antes de crear cada Payout.
     */
    public boolean canReceivePayouts() {
        return defaultPayoutMethod != null
                && defaultPayoutMethod.canBeUsedForPayout();
    }

}