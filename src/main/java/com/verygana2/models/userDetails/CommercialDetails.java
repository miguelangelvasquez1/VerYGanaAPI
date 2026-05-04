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

    @OneToMany(mappedBy = "commercial", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    private List<PayoutMethod> payoutMethods = new ArrayList<>();

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
}
