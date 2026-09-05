package com.verygana2.models.commercial;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.verygana2.models.enums.commercial.CommercialRoute;
import com.verygana2.models.enums.commercial.OnboardingStep;
import com.verygana2.models.enums.commercial.PersonType;
import com.verygana2.models.enums.commercial.TechIntegrationNeed;
import com.verygana2.models.enums.commercial.diagnostic.BusinessGoal;
import com.verygana2.models.enums.commercial.diagnostic.GrowthTool;
import com.verygana2.models.enums.commercial.diagnostic.InstitutionalTool;
import com.verygana2.models.enums.commercial.diagnostic.NetworkActor;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.userDetails.CommercialDetails;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Estado del flujo de registro comercial extendido (post "registro básico"):
 * aceptación de Términos y Condiciones, identificación jurídica, diagnóstico
 * comercial y clasificación automática de ruta (A-E).
 */
@Entity
@Table(name = "commercial_onboarding")
@Getter
@Setter
public class CommercialOnboarding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commercial_details_id", nullable = false, unique = true)
    private CommercialDetails commercialDetails;

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

    @Column(name = "legal_rep_first_name", length = 100)
    private String legalRepFirstName;

    @Column(name = "legal_rep_last_name", length = 100)
    private String legalRepLastName;

    @Column(name = "economic_activity_description", length = 500)
    private String economicActivityDescription;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "legal_identification_completed_at")
    private ZonedDateTime legalIdentificationCompletedAt;

    // ==================== PASO 4: DIAGNÓSTICO COMERCIAL ====================
    // Cuestionario del "Insumo técnico de caracterización empresarial": las
    // respuestas de una sola opción viven en el @Embeddable diagnosticAnswers;
    // las de selección múltiple (M-1, E-3, D-3, P-1) en las colecciones de abajo.

    @Embedded
    private CommercialDiagnosticAnswers diagnosticAnswers = new CommercialDiagnosticAnswers();

    /** M-1: beneficio anhelado. Hasta 3, ordenado por prioridad. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commercial_onboarding_business_goals", joinColumns = @JoinColumn(name = "commercial_onboarding_id"))
    @OrderColumn(name = "priority")
    @Enumerated(EnumType.STRING)
    @Column(name = "business_goal", length = 40)
    private List<BusinessGoal> businessGoals = new ArrayList<>();

    /** E-3: herramientas institucionales. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commercial_onboarding_institutional_tools", joinColumns = @JoinColumn(name = "commercial_onboarding_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "institutional_tool", length = 30)
    private Set<InstitutionalTool> institutionalTools = new HashSet<>();

    /** D-3: actores de la red comercial independiente. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commercial_onboarding_network_actors", joinColumns = @JoinColumn(name = "commercial_onboarding_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "network_actor", length = 30)
    private Set<NetworkActor> commercialNetworkActors = new HashSet<>();

    /** P-1: herramientas para crecer. Hasta 5, ordenado por prioridad. */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commercial_onboarding_growth_tools", joinColumns = @JoinColumn(name = "commercial_onboarding_id"))
    @OrderColumn(name = "priority")
    @Enumerated(EnumType.STRING)
    @Column(name = "growth_tool", length = 40)
    private List<GrowthTool> growthTools = new ArrayList<>();

    // Ruta alternativa de integración técnica (Ruta D): si el POST del diagnóstico trae
    // techIntegrationNeeds, se ignora el cuestionario y estos campos alimentan el panel
    // de asesor (CommercialContractServiceImpl) y la plantilla del contrato. Vacíos en
    // el flujo normal de cuestionario.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "commercial_onboarding_tech_needs", joinColumns = @JoinColumn(name = "commercial_onboarding_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "tech_need", length = 30)
    private Set<TechIntegrationNeed> techIntegrationNeeds = new HashSet<>();

    @Column(name = "integration_details", length = 1000)
    private String integrationDetails;

    @Column(name = "diagnostic_completed_at")
    private ZonedDateTime diagnosticCompletedAt;

    // ==================== PASO 5: CLASIFICACIÓN AUTOMÁTICA ====================

    @Enumerated(EnumType.STRING)
    @Column(name = "route", length = 5)
    private CommercialRoute route;

    @Column(name = "route_explanation", length = 1000)
    private String routeExplanation;

    /**
     * true cuando la recomendación es aproximada: faltan señales o el perfil está
     * en el borde entre modalidades (§3.4/§3.5 del insumo técnico). El empresario
     * igual continúa por el flujo normal; el front lo muestra como preliminar.
     */
    @Column(name = "route_preliminary", nullable = false)
    private boolean routePreliminary = false;

    /**
     * true cuando la modalidad recomendada es "candidata a Empresa Premium":
     * informativo para que el front muestre "sujeto a documentos, verificación y
     * aprobación". No bloquea el flujo (a diferencia de requiresSpecialNegotiation).
     */
    @Column(name = "verification_required", nullable = false)
    private boolean verificationRequired = false;

    @Column(name = "classified_at")
    private ZonedDateTime classifiedAt;

    @Column(name = "route_confirmed", nullable = false)
    private boolean routeConfirmed = false;

    @Column(name = "route_confirmed_at")
    private ZonedDateTime routeConfirmedAt;

    /**
     * true para Rutas D (integración técnica, seteado en submitDiagnostic) y E
     * (negociación especial, seteado en acceptPlan) — un único campo para "esta
     * cuenta necesita que un asesor de VERYGANA confirme condiciones antes de
     * generar el contrato" (ver requireGenerationReady). El motivo específico vive
     * en integrationDetails (D) o specialNegotiationDetails (E).
     */
    @Column(name = "requires_special_negotiation")
    private Boolean requiresSpecialNegotiation;

    /** Cuándo compliance resolvió la negociación y desbloqueó la generación del contrato. */
    @Column(name = "special_negotiation_resolved_at")
    private ZonedDateTime specialNegotiationResolvedAt;

    @Column(name = "special_negotiation_details", length = 1000)
    private String specialNegotiationDetails;

    // ==================== PASO 6-7: PLAN Y RESUMEN ECONÓMICO ====================
    // Snapshot de las condiciones económicas en el momento de la aceptación: el
    // Plan puede cambiar después, pero lo que el comercial aceptó queda fijo aquí
    // y es lo que se imprime en el contrato (paso 9).

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_plan_id")
    private Plan selectedPlan;

    @Column(name = "monthly_fee_cents_snapshot")
    private Long monthlyFeeCentsSnapshot;

    @Column(name = "min_investment_cents_snapshot")
    private Long minInvestmentCentsSnapshot;

    @Column(name = "max_investment_cents_snapshot")
    private Long maxInvestmentCentsSnapshot;

    /** Monto que el empresario se comprometió a invertir dentro del rango del plan. Null para BASIC. */
    @Column(name = "investment_amount_cents_snapshot")
    private Long investmentAmountCentsSnapshot;

    /** Duración del contrato en meses. Solo aplica a BASIC (suscripción con tarifa fija). Null para STANDARD/PREMIUM. */
    @Column(name = "contract_duration_months")
    private Integer contractDurationMonths;

    @Column(name = "sale_commission_pct_snapshot")
    private Integer saleCommissionPctSnapshot;

    @Column(name = "max_keys_pct_snapshot")
    private Integer maxKeysPctSnapshot;

    @Column(name = "plan_accepted_at")
    private ZonedDateTime planAcceptedAt;

    // ==================== OVERRIDES DE CAPACIDADES (NEGOCIACIÓN RUTA E) ====================
    // Ajustes a la medida para ESTE empresario en particular, por fuera de lo que su
    // Plan otorga por defecto — resultado de una negociación especial (ver
    // requiresSpecialNegotiation / resolveAdvisorNegotiation). null = sin override, se
    // usa el valor del Plan/PlanFeature (ver EffectivePlanResolver). -1 en los límites
    // enteros significa ilimitado, igual que en PlanFeature.

    @Column(name = "can_advertise_override")
    private Boolean canAdvertiseOverride;

    @Column(name = "can_use_games_override")
    private Boolean canUseGamesOverride;

    @Column(name = "can_use_surveys_override")
    private Boolean canUseSurveysOverride;

    @Column(name = "can_sell_directly_override")
    private Boolean canSellDirectlyOverride;

    @Column(name = "can_have_pets_override")
    private Boolean canHavePetsOverride;

    @Column(name = "can_promote_ally_products_override")
    private Boolean canPromoteAllyProductsOverride;

    @Column(name = "max_products_override")
    private Integer maxProductsOverride;

    @Column(name = "max_ads_override")
    private Integer maxAdsOverride;

    @Column(name = "max_branded_games_override")
    private Integer maxBrandedGamesOverride;

    @Column(name = "max_surveys_override")
    private Integer maxSurveysOverride;

    @Column(name = "visibility_boost_pct_override")
    private java.math.BigDecimal visibilityBoostPctOverride;

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
