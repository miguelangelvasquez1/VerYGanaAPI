package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.user.commercial.onboarding.ContractSummaryResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.commercial.CommercialContractRepository;
import com.verygana2.repositories.commercial.PlanChangeRequestRepository;
import com.verygana2.services.interfaces.commercial.CommercialContractService;
import com.verygana2.services.plans.PlanChangeAssetValidator;

import jakarta.persistence.EntityManager;

/**
 * Reproduce la carrera "recarga vs. cambio de plan": un comercial STANDARD/PREMIUM
 * dispara {@code requestRecharge} y {@code requestPlanChange} a la vez. Ambos
 * métodos son read-then-write (comprueban "¿ya hay una recarga / un cambio de
 * plan en curso?" y luego crean el contrato), así que sin serialización los dos
 * pueden colarse y dejar al comercial con una recarga firmándose y un cambio de
 * plan a la vez.
 *
 * El lock pesimista de {@code CommercialDetailsRepository.findByIdForUpdate}
 * (que ambos flujos toman al entrar) serializa la carrera por comercial: el
 * segundo hilo espera a que el primero haga commit, ve el artefacto recién
 * creado y falla ordenadamente con {@link BusinessException}.
 *
 * Se ejercita contra H2 real, cada llamada en su propia transacción (igual que
 * en producción vía los proxies {@code @Transactional} de los servicios).
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:recharge-planchange-concurrency-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.hikari.maximum-pool-size=10"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.context.annotation.Import({ PlanServiceImpl.class, PlanChangeRequestServiceImpl.class })
@DisplayName("Recarga vs. cambio de plan — solicitudes concurrentes (integración H2)")
class RechargePlanChangeConcurrencyIntegrationTest {

    private static final long STANDARD_MIN_CENTS = 100_000_000L;
    private static final long STANDARD_MAX_CENTS = 999_999_900L;
    private static final long RECHARGE_AMOUNT_CENTS = 150_000_000L;

    /**
     * Los dos hilos hacen countDown al llegar (dentro de generateFor) justo
     * después de pasar sus chequeos de guarda. Si el lock funciona, solo un hilo
     * llega aquí — el otro está bloqueado en findByIdForUpdate — así que este
     * await agota su timeout (2s) y el flujo continúa normal. Si el lock NO
     * funciona, ambos hilos llegan, el latch baja a 0 al instante y los dos
     * confirman su contrato: el test detecta las dos "victorias".
     */
    private final CountDownLatch bothInsideGuard = new CountDownLatch(2);

    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private PlanServiceImpl planService;
    @Autowired private PlanChangeRequestServiceImpl planChangeRequestService;
    @Autowired private CommercialContractRepository contractRepository;
    @Autowired private PlanChangeRequestRepository planChangeRequestRepository;

    @MockitoBean private CommercialContractService commercialContractService;
    @MockitoBean private PlanChangeAssetValidator planChangeAssetValidator;
    @MockitoBean private com.verygana2.services.wompi.WompiService wompiService;
    @MockitoBean private com.verygana2.services.interfaces.finance.TreasuryService treasuryService;
    @MockitoBean private TreasuryConfig treasuryConfig;
    @MockitoBean private com.verygana2.services.interfaces.finance.WalletService walletService;
    @MockitoBean private com.verygana2.services.plans.InvestmentService investmentService;
    @MockitoBean private com.verygana2.services.plans.EffectivePlanResolver effectivePlanResolver;
    @MockitoBean private com.verygana2.services.interfaces.EmailService emailService;
    @MockitoBean private com.verygana2.services.interfaces.NotificationService notificationService;
    @MockitoBean private com.verygana2.mappers.CommercialOnboardingMapper commercialOnboardingMapper;

    @BeforeEach
    void stubContractGeneration() {
        lenient().when(planChangeAssetValidator.findBlockers(anyLong(), any())).thenReturn(List.of());

        // El mock persiste un contrato real (en la transacción del hilo que llama)
        // para que el otro flujo lo detecte al tomar el lock, igual que haría el
        // CommercialContractServiceImpl real.
        lenient().when(commercialContractService.generateFor(any(), eq(ContractPurpose.RECHARGE), any(), any()))
                .thenAnswer(inv -> persistContract(inv.getArgument(0), ContractPurpose.RECHARGE,
                        ContractStatus.APPROVED, inv.getArgument(2), null));

        lenient().when(commercialContractService.generateFor(any(), eq(ContractPurpose.PLAN_CHANGE), any(), any()))
                .thenAnswer(inv -> persistContract(inv.getArgument(0), ContractPurpose.PLAN_CHANGE,
                        ContractStatus.PENDING_BUSINESS_REVIEW, null, inv.getArgument(3)));
    }

    private ContractSummaryResponseDTO persistContract(CommercialDetails commercial, ContractPurpose purpose,
            ContractStatus status, Long amountCents, Plan targetPlan) {
        // Se llega aquí sólo tras pasar los chequeos de guarda de cada flujo.
        bothInsideGuard.countDown();
        try {
            bothInsideGuard.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        CommercialContract contract = new CommercialContract();
        contract.setCommercial(em.getReference(CommercialDetails.class, commercial.getId()));
        contract.setPurpose(purpose);
        contract.setStatus(status);
        contract.setObjectKey("commercial-contracts/test/" + purpose.name().toLowerCase() + ".pdf");
        contract.setVersion(1);
        contract.setGeneratedAt(ZonedDateTime.now());
        contract.setAmountCentsSnapshot(amountCents);
        contract.setTargetPlan(targetPlan);
        CommercialContract saved = contractRepository.save(contract);

        ContractSummaryResponseDTO dto = new ContractSummaryResponseDTO();
        dto.setContractId(saved.getId());
        return dto;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("requestRecharge y requestPlanChange a la vez: exactamente uno gana, el otro falla con BusinessException")
    void rechargeAndPlanChange_cannotBothWin() throws Exception {
        TransactionTemplate setupTx = new TransactionTemplate(txManager);
        Long commercialId = setupTx.execute(status -> {
            persistPlan(PlanCode.STANDARD, STANDARD_MIN_CENTS, STANDARD_MAX_CENTS);
            persistPlan(PlanCode.PREMIUM, 1_000_000_000L, null);
            return persistCommercialOnStandard();
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startGate = new CountDownLatch(1);

        // Retorna null en éxito, o la excepción con la que falló.
        Callable<Exception> rechargeTask = () -> {
            CommercialDetails ref = new CommercialDetails();
            ref.setId(commercialId);
            bothReady.countDown();
            startGate.await();
            try {
                planService.requestRecharge(ref, RECHARGE_AMOUNT_CENTS);
                return null;
            } catch (RuntimeException ex) {
                return ex;
            }
        };
        Callable<Exception> planChangeTask = () -> {
            bothReady.countDown();
            startGate.await();
            try {
                planChangeRequestService.requestPlanChange(commercialId, PlanCode.PREMIUM, null);
                return null;
            } catch (RuntimeException ex) {
                return ex;
            }
        };

        Future<Exception> rechargeResult = pool.submit(rechargeTask);
        Future<Exception> planChangeResult = pool.submit(planChangeTask);

        bothReady.await(10, TimeUnit.SECONDS);
        startGate.countDown();

        Exception rechargeFailure = rechargeResult.get(20, TimeUnit.SECONDS);
        Exception planChangeFailure = planChangeResult.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        List<Exception> failures = new ArrayList<>();
        int successCount = 0;
        if (rechargeFailure == null) successCount++; else failures.add(rechargeFailure);
        if (planChangeFailure == null) successCount++; else failures.add(planChangeFailure);

        assertThat(successCount)
                .as("exactamente una de las dos solicitudes debe prosperar")
                .isEqualTo(1);
        assertThat(failures)
                .as("la solicitud perdedora debe fallar con BusinessException, no con un error crudo de BD")
                .singleElement()
                .isInstanceOf(BusinessException.class);

        // ── Estado real en BD: como mucho un artefacto "abierto" ───────────
        em.clear();
        long openRecharges = contractRepository.findOpenRechargeContracts(commercialId).size();
        long openPlanChanges = planChangeRequestRepository
                .findByCommercial_IdAndStatusNotIn(commercialId, List.of(
                        com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus.APPLIED,
                        com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus.REJECTED,
                        com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus.CANCELLED))
                .size();

        assertThat(openRecharges + openPlanChanges)
                .as("no pueden coexistir una recarga y un cambio de plan en curso")
                .isEqualTo(1L);
    }

    // ==================== fixtures ====================

    private void persistPlan(PlanCode code, Long minCents, Long maxCents) {
        Plan plan = Plan.builder()
                .version(1)
                .active(true)
                .code(code)
                .name(code.name())
                .saleCommissionPct(10)
                .maxKeysPct(20)
                .minInvestmentCents(minCents)
                .maxInvestmentCents(maxCents)
                .monthlyPriceCents(code == PlanCode.BASIC ? 20_000_000L : null)
                .build();
        em.persist(plan);
        em.flush();
    }

    private Long persistCommercialOnStandard() {
        Plan standard = em.createQuery("SELECT p FROM Plan p WHERE p.code = :c", Plan.class)
                .setParameter("c", PlanCode.STANDARD)
                .getSingleResult();

        User user = new User();
        user.setEmail("commercial-concurrency@test.com");
        user.setPhoneNumber("3009998877");
        user.setPassword("hash");
        user.setRole(Role.COMMERCIAL);
        user.setUserState(UserState.ACTIVE);
        user.setPublicId(UUID.randomUUID());
        user.setRegisteredDate(ZonedDateTime.now());
        em.persist(user);
        em.flush();

        CommercialDetails commercial = new CommercialDetails();
        commercial.setUser(user);
        commercial.setCompanyName("Concurrency S.A.S.");
        commercial.setCurrentPlan(standard);
        em.persist(commercial);
        em.flush();

        return commercial.getId();
    }
}
