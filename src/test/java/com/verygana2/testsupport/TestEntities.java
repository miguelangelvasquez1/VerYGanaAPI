package com.verygana2.testsupport;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import com.verygana2.models.Avatar;
import com.verygana2.models.Category;
import com.verygana2.models.Department;
import com.verygana2.models.Municipality;
import com.verygana2.models.User;
import com.verygana2.models.enums.DocumentType;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Feature;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;

import jakarta.persistence.EntityManager;

/**
 * Fábrica de entidades para tests de integración.
 * Construye el grafo mínimo que exige ConsumerDetails
 * (Department → Municipality, Avatar, Category, User).
 */
public final class TestEntities {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private TestEntities() {
    }

    /**
     * Persiste las entidades de catálogo compartidas y retorna un consumer listo.
     * Cada llamada genera email/teléfono/hash únicos.
     */
    public static ConsumerDetails persistConsumer(EntityManager em) {
        ConsumerDetails consumer = newConsumer(em);
        em.persist(consumer);
        em.flush();
        return consumer;
    }

    /**
     * Construye un consumer SIN persistirlo (sus dependencias sí quedan
     * persistidas). Útil para probar flujos de registro como
     * ReferralService.prepareNewConsumer.
     */
    public static ConsumerDetails newConsumer(EntityManager em) {
        long n = SEQ.getAndIncrement();

        Department department = em.find(Department.class, "63");
        if (department == null) {
            department = new Department();
            department.setCode("63");
            department.setName("Quindío");
            em.persist(department);
        }

        Municipality municipality = em.find(Municipality.class, "63001");
        if (municipality == null) {
            municipality = new Municipality();
            municipality.setCode("63001");
            municipality.setName("Armenia");
            municipality.setDepartment(department);
            em.persist(municipality);
        }

        Avatar avatar = new Avatar();
        avatar.setName("avatar-test-" + n);
        avatar.setImageUrl("https://cdn.test/avatar.png");
        em.persist(avatar);

        Category category = new Category();
        category.setName("categoria-test-" + n);
        em.persist(category);

        User user = new User();
        user.setEmail("consumer" + n + "@test.com");
        user.setPhoneNumber("300000" + String.format("%04d", n));
        user.setPassword("hash");
        user.setRole(Role.CONSUMER);
        user.setUserState(UserState.ACTIVE);
        user.setRegisteredDate(ZonedDateTime.now());
        em.persist(user);

        ConsumerDetails consumer = new ConsumerDetails();
        consumer.setUser(user);
        consumer.setUserHash("hash-" + n);
        consumer.setUserName("user" + n);
        consumer.setAvatar(avatar);
        consumer.setName("Nombre" + n);
        consumer.setLastName("Apellido" + n);
        consumer.setDepartmentName("Quindío");
        consumer.setMunicipalityName("Armenia");
        consumer.setMunicipality(municipality);
        consumer.setCategories(List.of(category));
        consumer.setReferralCode("CODE" + String.format("%04d", n));
        consumer.setDocumentType(DocumentType.CC);
        consumer.setDocumentNumber("100000" + n);

        em.flush();
        return consumer;
    }

    /**
     * Persiste las entidades de catálogo compartidas y retorna un commercial
     * (comercio vendedor) listo, SIN currentPlan asignado (currentPlan es
     * nullable en CommercialDetails). Cada llamada genera email/teléfono/nit
     * únicos.
     */
    public static CommercialDetails persistCommercial(EntityManager em) {
        long n = SEQ.getAndIncrement();

        Department department = em.find(Department.class, "63");
        if (department == null) {
            department = new Department();
            department.setCode("63");
            department.setName("Quindío");
            em.persist(department);
        }

        Municipality municipality = em.find(Municipality.class, "63001");
        if (municipality == null) {
            municipality = new Municipality();
            municipality.setCode("63001");
            municipality.setName("Armenia");
            municipality.setDepartment(department);
            em.persist(municipality);
        }

        User user = new User();
        user.setEmail("commercial" + n + "@test.com");
        user.setPhoneNumber("310000" + String.format("%04d", n));
        user.setPassword("hash");
        user.setRole(Role.COMMERCIAL);
        user.setUserState(UserState.ACTIVE);
        user.setRegisteredDate(ZonedDateTime.now());
        em.persist(user);

        CommercialDetails commercial = new CommercialDetails();
        commercial.setUser(user);
        commercial.setCompanyName("Comercio Test " + n);
        commercial.setNit("NIT" + String.format("%06d", n));
        commercial.setMunicipality(municipality);
        commercial.setMunicipalityName("Armenia");
        commercial.setDepartmentName("Quindío");

        em.persist(commercial);
        em.flush();
        return commercial;
    }

    /**
     * Igual que {@link #persistCommercial(EntityManager)} pero además asigna
     * un currentPlan con el PlanCode indicado. Si ya existe un Plan
     * persistido con ese código (Plan.code es unique) lo reutiliza, en vez
     * de intentar crear uno duplicado.
     */
    public static CommercialDetails persistCommercial(EntityManager em, PlanCode planCode) {
        CommercialDetails commercial = persistCommercial(em);

        List<Plan> existingPlans = em.createQuery(
                "select p from Plan p where p.code = :code", Plan.class)
                .setParameter("code", planCode)
                .getResultList();

        Plan plan;
        if (!existingPlans.isEmpty()) {
            plan = existingPlans.get(0);
        } else {
            long n = SEQ.getAndIncrement();
            plan = new Plan();
            plan.setVersion(1);
            plan.setActive(true);
            plan.setCode(planCode);
            plan.setName(planCode.name() + " Test " + n);
            plan.setSaleCommissionPct(10);
            plan.setMaxKeysPct(20);
            em.persist(plan);
            em.flush();
        }

        commercial.setCurrentPlan(plan);
        em.flush();
        return commercial;
    }

    /**
     * Crea y persiste un Wallet asociado al commercial dado con el saldo
     * indicado (en centavos). El saldo se fija con el setter directo (no con
     * el método de dominio {@code deposit()}, que lanza
     * IllegalArgumentException para montos <= 0 y por lo tanto no sirve para
     * dejar el wallet en 0). El status se calcula con
     * {@code Wallet.recalculateStatus()} para quedar coherente con el saldo:
     * EXHAUSTED si balanceCents == 0, ACTIVE en cualquier otro caso (no se
     * fija lastDepositAmountCents, así que el umbral de LOW_BALANCE queda en
     * 0 y nunca se dispara desde este helper).
     */
    public static Wallet persistWallet(EntityManager em, CommercialDetails commercial, long balanceCents) {
        Wallet wallet = Wallet.createFor(commercial);
        wallet.setBalanceCents(balanceCents);
        wallet.recalculateStatus();

        em.persist(wallet);
        em.flush();
        return wallet;
    }

    /**
     * Crea y persiste un PayoutMethod mínimo válido (tipo BANK_ACCOUNT) para
     * el commercial dado, con el VerificationStatus indicado. Todos los
     * campos NOT NULL de la entidad quedan seteados con valores dummy únicos
     * (alias, bankCode, accountNumber, accountHolderName, accountHolderDoc,
     * accountHolderDocType); bankAccountType también se fija porque es
     * relevante para BANK_ACCOUNT.
     */
    public static PayoutMethod persistPayoutMethod(EntityManager em, CommercialDetails commercial,
            VerificationStatus status) {
        long n = SEQ.getAndIncrement();

        PayoutMethod payoutMethod = PayoutMethod.builder()
                .commercial(commercial)
                .type(PayoutMethod.PayoutMethodType.BANK_ACCOUNT)
                .alias("Cuenta Test " + n)
                .bankCode("BANK" + String.format("%04d", n))
                .accountNumber("ACC" + String.format("%08d", n))
                .bankAccountType(PayoutMethod.BankAccountType.SAVINGS)
                .accountHolderName("Titular Test " + n)
                .accountHolderDoc("100000" + n)
                .accountHolderDocType(PayoutMethod.DocType.CC)
                .verificationStatus(status)
                .build();

        em.persist(payoutMethod);
        em.flush();
        return payoutMethod;
    }

    /**
     * Crea/reutiliza (Feature.code es unique) el Feature catálogo con el code
     * dado y tipo BOOLEAN, crea el PlanFeature asociándolo al plan con
     * boolValue, persiste y flush.
     */
    public static PlanFeature persistBoolPlanFeature(EntityManager em, Plan plan, String featureCode,
            Boolean boolValue) {
        Feature feature = findOrCreateFeature(em, featureCode, Feature.FeatureType.BOOLEAN);
        return persistPlanFeature(em, plan, feature, boolValue, null, null);
    }

    /**
     * Crea/reutiliza (Feature.code es unique) el Feature catálogo con el code
     * dado y tipo LIMIT, crea el PlanFeature asociándolo al plan con
     * intValue, persiste y flush.
     */
    public static PlanFeature persistIntPlanFeature(EntityManager em, Plan plan, String featureCode,
            Integer intValue) {
        Feature feature = findOrCreateFeature(em, featureCode, Feature.FeatureType.LIMIT);
        return persistPlanFeature(em, plan, feature, null, intValue, null);
    }

    /**
     * Crea/reutiliza (Feature.code es unique) el Feature catálogo con el code
     * dado y tipo PERCENTAGE, crea el PlanFeature asociándolo al plan con
     * decimalValue, persiste y flush.
     */
    public static PlanFeature persistDecimalPlanFeature(EntityManager em, Plan plan, String featureCode,
            BigDecimal decimalValue) {
        Feature feature = findOrCreateFeature(em, featureCode, Feature.FeatureType.PERCENTAGE);
        return persistPlanFeature(em, plan, feature, null, null, decimalValue);
    }

    private static PlanFeature persistPlanFeature(EntityManager em, Plan plan, Feature feature,
            Boolean boolValue, Integer intValue, BigDecimal decimalValue) {
        PlanFeature planFeature = PlanFeature.builder()
                .plan(plan)
                .feature(feature)
                .boolValue(boolValue)
                .intValue(intValue)
                .decimalValue(decimalValue)
                .build();

        em.persist(planFeature);
        em.flush();
        return planFeature;
    }

    private static Feature findOrCreateFeature(EntityManager em, String code, Feature.FeatureType type) {
        List<Feature> existingFeatures = em.createQuery(
                "select f from Feature f where f.code = :code", Feature.class)
                .setParameter("code", code)
                .getResultList();

        if (!existingFeatures.isEmpty()) {
            return existingFeatures.get(0);
        }

        Feature feature = Feature.builder()
                .code(code)
                .name(code)
                .type(type)
                .build();
        em.persist(feature);
        em.flush();
        return feature;
    }

    /**
     * Crea/reutiliza (TreasuryAccount.code es unique) la TreasuryAccount con
     * el código dado y deja su balanceCents en el valor indicado (si ya
     * existía, se actualiza el saldo para que quede consistente con lo
     * pedido por el caller).
     */
    public static TreasuryAccount persistTreasuryAccount(EntityManager em, TreasuryAccountCode code,
            long balanceCents) {
        List<TreasuryAccount> existingAccounts = em.createQuery(
                "select t from TreasuryAccount t where t.code = :code", TreasuryAccount.class)
                .setParameter("code", code)
                .getResultList();

        TreasuryAccount account;
        if (!existingAccounts.isEmpty()) {
            account = existingAccounts.get(0);
            account.setBalanceCents(balanceCents);
        } else {
            account = TreasuryAccount.builder()
                    .code(code)
                    .name(code.name())
                    .balanceCents(balanceCents)
                    .build();
            em.persist(account);
        }

        em.flush();
        return account;
    }
}