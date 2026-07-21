package com.verygana2.models.userDetails;

import java.util.ArrayList;
import java.util.List;

import com.verygana2.models.Municipality;
import com.verygana2.models.commercial.CommercialOnboarding;
import com.verygana2.models.enums.AnnualRevenueRange;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Subscription;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    // Razón social, NIT y el resto de datos de identificación jurídica se
    // capturan en el paso 3 del onboarding (submitLegalIdentification), no en
    // el registro básico — por eso quedan nullable aquí hasta ese momento.

    @Column(name = "company_name", length = 200)
    private String companyName;

    @Column(unique = true, length = 20)
    private String nit;

    // ==================== KYC / SAGRILAFT ====================

    @OneToOne(mappedBy = "commercialDetails", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private CommercialOnboarding onboarding;

    @Column(name = "ciiu_code", length = 10)
    private String ciiuCode;

    @Column(name = "mercantile_registration", unique = true, length = 20)
    private String mercantileRegistration;

    @Enumerated(EnumType.STRING)
    @Column(name = "legal_rep_doc_type", length = 5)
    private DocumentType legalRepDocType;

    @Column(name = "legal_rep_doc_number", length = 20)
    private String legalRepDocNumber;

    @Column(name = "is_pep", nullable = false)
    private boolean pep = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "annual_income_range", length = 30)
    private AnnualRevenueRange annualIncomeRange;

    // ===== LOCATION =====

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "municipality_code")
    private Municipality municipality;

    @Column(name = "municipality_name", length = 100)
    private String municipalityName;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    // ===== PAYOUT METHODS =====

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

    // ===== BUSINESS METHODS =====

    public boolean canReceivePayouts() {
        return defaultPayoutMethod != null
                && defaultPayoutMethod.canBeUsedForPayout();
    }

    /**
     * Checks whether the business user has active access to the platform.
     *
     * BASIC    → requires an active Subscription
     * STANDARD/PREMIUM → requires an operational Wallet (ACTIVE or LOW_BALANCE)
     */
    public boolean hasActiveAccess() {
        if (currentPlan == null) return false;

        if (currentPlan.isMonthlySubscription()) {
            // Basic plan: access via monthly subscription
            return subscriptions.stream()
                    .anyMatch(Subscription::isCurrentlyActive);
        }

        // Standard/premium plans: access via available balance
        return wallet != null && wallet.isOperational();
    }

    /**
     * Checks whether new interactions (ads, games, etc.) can be activated.
     * Distinct from hasActiveAccess() — also verifies sufficient balance
     * for the minimum activation cost.
     *
     * @param minActivationCents minimum cost of the interaction to activate
     */
    public boolean canActivateInteraction(long minActivationCents) {
        if (!hasActiveAccess()) return false;
        if (currentPlan.isMonthlySubscription()) return true; // basic plan has no wallet
        return wallet != null && wallet.hasFundsFor(minActivationCents);
    }

    /**
     * Returns the current plan as a human-readable string.
     * Useful for logs and notifications.
     */
    public String getCurrentPlanName() {
        return currentPlan != null ? currentPlan.getName() : "No plan";
    }

}