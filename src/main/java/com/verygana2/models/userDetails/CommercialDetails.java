package com.verygana2.models.userDetails;

import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Subscription;

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

    @OneToMany(mappedBy = "commercial", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PayoutMethod> payoutMethods = new ArrayList<>();

    @OneToMany(mappedBy = "commercial", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<Subscription> subscriptions = new ArrayList<>();

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "default_payout_method_id")
    private PayoutMethod defaultPayoutMethod;

    // ===== WALLET =====

    @OneToOne(mappedBy = "commercial", fetch = FetchType.LAZY)
    private Wallet wallet;

    // ===== PLAN =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_plan_id")
    private Plan currentPlan;

    // ===== MÉTODOS DE NEGOCIO =====

    public boolean canReceivePayouts() {
        return defaultPayoutMethod != null
                && defaultPayoutMethod.canBeUsedForPayout();
    }

    /**
 * Verifica si el empresario tiene acceso activo a la plataforma.
 *
 * BASIC    → necesita Subscription vigente
 * STANDARD/PREMIUM → necesita Wallet operacional (ACTIVE o LOW_BALANCE)
 */
public boolean hasActiveAccess() {
    if (currentPlan == null) return false;
 
    if (currentPlan.isMonthlySubscription()) {
        // Plan básico: acceso por suscripción mensual
        return subscriptions.stream()
                .anyMatch(Subscription::isCurrentlyActive);
    }
 
    // Planes estándar/premium: acceso por saldo disponible
    return wallet != null && wallet.isOperational();
}
 
/**
 * Verifica si puede activar nuevas interacciones (anuncios, juegos, etc.).
 * Distinto de hasActiveAccess() — aquí también se verifica que tenga
 * saldo suficiente para el costo mínimo de activación.
 *
 * @param minActivationCents costo mínimo de la interacción a activar
 */
public boolean canActivateInteraction(long minActivationCents) {
    if (!hasActiveAccess()) return false;
    if (currentPlan.isMonthlySubscription()) return true; // básico no tiene wallet
    return wallet != null && wallet.hasFundsFor(minActivationCents);
}
 
/**
 * Retorna el plan actual como texto legible.
 * Útil para logs y notificaciones.
 */
public String getCurrentPlanName() {
    return currentPlan != null ? currentPlan.getName() : "Sin plan";
}
 
}
