package com.verygana2.models.commercial;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

import com.verygana2.models.User;
import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.PaymentPeriodicity;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.models.enums.commercial.PrimaryGoal;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;
import com.verygana2.models.finance.plans.Plan;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Estado del flujo de registro comercial extendido (post "registro básico"):
 * aceptación de Términos y Condiciones, identificación jurídica, diagnóstico
 * comercial y clasificación automática de ruta (A-E).
 */
@Entity
@Table(name = "commercial_onboarding")
@Data
public class CommercialOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_step", nullable = false, length = 40)
    private OnboardingStep currentStep = OnboardingStep.TERMS_PENDING;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    // ==================== PASO 2: TÉRMINOS Y CONDICIONES ====================
    // El PDF y su URL viven en el frontend (env del frontend); el backend solo
    // registra qué versión/documento fue mostrado y aceptado, y cuándo/desde dónde.

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "terms_document_url", length = 500)
    private String termsDocumentUrl;

    @Column(name = "terms_published_date")
    private java.time.LocalDate termsPublishedDate;

    @Column(name = "terms_accepted_at")
    private ZonedDateTime termsAcceptedAt;

    @Column(name = "terms_accepted_ip", length = 64)
    private String termsAcceptedIp;

    @Column(name = "terms_accepted_user_agent", length = 300)
    private String termsAcceptedUserAgent;

    // ==================== PASO 3: IDENTIFICACIÓN JURÍDICA ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "person_type", length = 20)
    private PersonType personType;

    @Column(name = "legal_rep_full_name", length = 200)
    private String legalRepFullName;

    @Column(name = "economic_activity_description", length = 500)
    private String economicActivityDescription;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "legal_identification_completed_at")
    private ZonedDateTime legalIdentificationCompletedAt;

    // ==================== PASO 4: DIAGNÓSTICO COMERCIAL ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_goal", length = 30)
    private PrimaryGoal primaryGoal; // Q3

    @Column(name = "wants_fixed_fee")
    private Boolean wantsFixedFee; // Q4

    @Column(name = "accepts_commission_on_sale_only")
    private Boolean acceptsCommissionOnSaleOnly; // Q5

    @Column(name = "max_promotional_keys_percentage")
    private Integer maxPromotionalKeysPercentage; // Q6 (0-100)

    @Column(name = "accepted_commission_percentage")
    private Integer acceptedCommissionPercentage; // Q7 (0-100)

    @Column(name = "requires_custom_games")
    private Boolean requiresCustomGames; // Q8

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commercial_onboarding_tech_needs", joinColumns = @JoinColumn(name = "commercial_onboarding_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tech_need", length = 30)
    private Set<TechIntegrationNeed> techIntegrationNeeds = new HashSet<>(); // Q9

    @Column(name = "regulated_sector")
    private Boolean regulatedSector; // Q10

    @Column(name = "requires_special_negotiation")
    private Boolean requiresSpecialNegotiation; // Q11

    @Column(name = "contract_duration_months")
    private Integer contractDurationMonths; // Q12a

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_periodicity", length = 20)
    private PaymentPeriodicity paymentPeriodicity; // Q12b

    @Column(name = "termination_terms", length = 500)
    private String terminationTerms; // Q12c

    @Column(name = "diagnostic_completed_at")
    private ZonedDateTime diagnosticCompletedAt;

    // ==================== PASO 5: CLASIFICACIÓN AUTOMÁTICA ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 5)
    private CommercialRoute route;

    @Column(name = "route_explanation", length = 1000)
    private String routeExplanation;

    @Column(name = "classified_at")
    private ZonedDateTime classifiedAt;

    @Column(name = "route_confirmed", nullable = false)
    private boolean routeConfirmed = false;

    @Column(name = "route_confirmed_at")
    private ZonedDateTime routeConfirmedAt;

    // ==================== PASO 6-7: PLAN Y RESUMEN ECONÓMICO ====================
    // Snapshot de las condiciones económicas en el momento de la aceptación: el
    // Plan puede cambiar después, pero lo que el comercial aceptó queda fijo aquí
    // y es lo que se imprime en el contrato (paso 9).

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_plan_id")
    private Plan selectedPlan;

    @Column(name = "requires_advisor_contact")
    private Boolean requiresAdvisorContact;

    @Column(name = "monthly_fee_cents_snapshot")
    private Long monthlyFeeCentsSnapshot;

    @Column(name = "min_investment_cents_snapshot")
    private Long minInvestmentCentsSnapshot;

    @Column(name = "max_investment_cents_snapshot")
    private Long maxInvestmentCentsSnapshot;

    @Column(name = "sale_commission_pct_snapshot")
    private Integer saleCommissionPctSnapshot;

    @Column(name = "max_keys_pct_snapshot")
    private Integer maxKeysPctSnapshot;

    @Column(name = "tax_note_snapshot", length = 1000)
    private String taxNoteSnapshot;

    @Column(name = "liquidation_conditions_snapshot", length = 1000)
    private String liquidationConditionsSnapshot;

    @Column(name = "plan_accepted_at")
    private ZonedDateTime planAcceptedAt;

    // ==================== PASO 8: CARGA DOCUMENTAL ====================

    @Column(name = "documents_completed_at")
    private ZonedDateTime documentsCompletedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = ZonedDateTime.now();
        }
    }
}
