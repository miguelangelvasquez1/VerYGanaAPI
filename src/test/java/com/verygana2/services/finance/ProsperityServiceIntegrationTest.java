package com.verygana2.services.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.config.TreasuryConfig;
import com.verygana2.dtos.prosperity.ProsperityAdjustmentRequestDTO;
import com.verygana2.dtos.prosperity.ProsperityMovementResponseDTO;
import com.verygana2.dtos.prosperity.ProsperityReversalRequestDTO;
import com.verygana2.dtos.prosperity.ProsperitySummaryResponseDTO;
import com.verygana2.exceptions.BusinessException;
import com.verygana2.models.enums.finance.ProsperityEntryType;
import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.finance.plans.PlanFeature;
import com.verygana2.models.finance.prosperity.ProsperityAccount;
import com.verygana2.models.finance.prosperity.ProsperityLedgerEntry;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.repositories.finance.prosperity.ProsperityAccountRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityLedgerEntryRepository;
import com.verygana2.repositories.finance.prosperity.ProsperityThresholdRepository;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Motor de Prosperidad (MP-05) contra H2 real: cubre los criterios de aceptación del
 * documento (primera inversión, inversión sucesiva, doble evento, reverso, conciliación,
 * consumo parcial, agotamiento exacto, venta superior al saldo, intento de reinicio) y
 * la concurrencia sobre el mismo Saldo.
 *
 * Las compras se arman en memoria (Purchase/PurchaseItem sin persistir, con ids fijos):
 * absorbPurchase solo lee sus montos y el libro guarda el id del ítem como origen, así
 * que no hace falta montar consumidor, producto y stock.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:prosperity-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // V1__baseline.sql es MySQL puro: el esquema de prueba lo genera Hibernate.
        "spring.flyway.enabled=false",
        "spring.datasource.hikari.maximum-pool-size=10"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ ProsperityServiceImpl.class, com.verygana2.mappers.finance.ProsperityMapperImpl.class })
@DisplayName("ProsperityService — Umbral, Saldo y Libro Mayor (integración H2)")
class ProsperityServiceIntegrationTest {

    private static final long ONE_MILLION = 100_000_000L; // $1.000.000 en centavos
    private static final AtomicLong ITEM_IDS = new AtomicLong(10_000);

    @Autowired private ProsperityServiceImpl service;
    @Autowired private EntityManager em;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private ProsperityAccountRepository accountRepository;
    @Autowired private ProsperityThresholdRepository thresholdRepository;
    @Autowired private ProsperityLedgerEntryRepository ledgerRepository;

    @MockitoBean private TreasuryConfig treasuryConfig;

    @BeforeEach
    void stubVat() {
        when(treasuryConfig.getVatPct()).thenReturn(19);
    }

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    private Plan plan(PlanCode code, Integer multiplier) {
        CommercialDetails probe = TestEntities.persistCommercial(em, code);
        Plan plan = probe.getCurrentPlan();
        boolean hasFeature = plan.getFeatures().stream()
                .anyMatch(pf -> pf.getFeature().getCode().equals(ProsperityServiceImpl.MULTIPLIER_FEATURE));
        if (multiplier != null && !hasFeature) {
            PlanFeature pf = TestEntities.persistIntPlanFeature(em, plan,
                    ProsperityServiceImpl.MULTIPLIER_FEATURE, multiplier);
            plan.getFeatures().add(pf);
        }
        return plan;
    }

    private CommercialDetails standardCommercial() {
        Plan standard = plan(PlanCode.STANDARD, 4);
        CommercialDetails commercial = TestEntities.persistCommercial(em);
        commercial.setCurrentPlan(standard);
        TestEntities.persistWallet(em, commercial, 0L);
        em.flush();
        return commercial;
    }

    private Investment confirmedInvestment(CommercialDetails commercial, Plan plan, long netCents) {
        Wallet wallet = em.createQuery("select w from Wallet w where w.commercial.id = :id", Wallet.class)
                .setParameter("id", commercial.getId()).getSingleResult();
        Investment investment = Investment.builder()
                .wallet(wallet)
                .planAtDeposit(plan)
                .depositAmountCents(netCents)
                .vatAmountCents(netCents * 19 / 100)
                .confirmed(true)
                .confirmedAt(ZonedDateTime.now(ZoneOffset.UTC))
                .build();
        em.persist(investment);
        em.flush();
        return investment;
    }

    private Investment invest(CommercialDetails commercial, long netCents) {
        Investment investment = confirmedInvestment(commercial, commercial.getCurrentPlan(), netCents);
        service.generateThreshold(investment);
        return investment;
    }

    private static PurchaseItem item(CommercialDetails commercial, long priceCents, int pct) {
        PurchaseItem item = PurchaseItem.builder()
                .id(ITEM_IDS.incrementAndGet())
                .commercialId(commercial.getId())
                .unitPriceCents(priceCents)
                .subtotalCents(priceCents)
                .commissionPctApplied(pct)
                .build();
        item.applyCommission(priceCents, 19); // provisional, como en PurchaseServiceImpl
        return item;
    }

    private static Purchase purchase(PurchaseItem... items) {
        Purchase purchase = new Purchase();
        for (PurchaseItem item : items) {
            purchase.addItem(item);
        }
        purchase.calculateFinancials();
        return purchase;
    }

    private long balance(CommercialDetails commercial) {
        return accountRepository.findByCommercialId(commercial.getId()).orElseThrow().getBalanceCents();
    }

    // ─── Umbral ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Generación del Umbral")
    class Threshold {

        @Test
        @DisplayName("primera inversión: $1.000.000 + IVA genera $4.000.000 (solo sobre el neto)")
        void firstInvestment_generatesFourTimesNet() {
            CommercialDetails commercial = standardCommercial();

            Investment investment = invest(commercial, ONE_MILLION);

            var threshold = thresholdRepository.findByInvestmentId(investment.getId()).orElseThrow();
            assertThat(threshold.getMultiplier()).isEqualTo(4);
            assertThat(threshold.getInvestmentNetCents()).isEqualTo(ONE_MILLION);
            assertThat(threshold.getGeneratedCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(balance(commercial)).isEqualTo(4 * ONE_MILLION);

            ProsperityLedgerEntry entry = ledgerRepository
                    .findByIdempotencyKey("THRESHOLD:INV:" + investment.getId()).orElseThrow();
            assertThat(entry.getType()).isEqualTo(ProsperityEntryType.THRESHOLD_GENERATED);
            assertThat(entry.getBalanceBeforeCents()).isZero();
            assertThat(entry.getBalanceAfterCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(entry.getSequence()).isEqualTo(1L);
        }

        @Test
        @DisplayName("inversión sucesiva (ejemplo del jefe): 4M − 2,5M absorbidos + 4M = 5,5M, sin reiniciar")
        void successiveInvestment_accumulatesOverRemainingBalance() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION);

            PurchaseItem sale = item(commercial, 250_000_000L, 10);
            service.absorbPurchase(purchase(sale));
            assertThat(balance(commercial)).isEqualTo(150_000_000L);

            invest(commercial, ONE_MILLION);

            ProsperityAccount account = accountRepository.findByCommercialId(commercial.getId()).orElseThrow();
            assertThat(account.getBalanceCents()).isEqualTo(550_000_000L);
            assertThat(account.getAccumulatedThresholdCents()).isEqualTo(8 * ONE_MILLION);
            assertThat(account.getTotalAbsorbedCents()).isEqualTo(250_000_000L); // la venta previa no se borra
            assertThat(thresholdRepository.findByAccountIdOrderByValidatedAtAsc(account.getId())).hasSize(2);
        }

        @Test
        @DisplayName("doble evento de la misma inversión: un solo Umbral, saldo sin duplicar")
        void duplicateEvent_isIdempotent() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = invest(commercial, ONE_MILLION);

            service.generateThreshold(investment);

            assertThat(balance(commercial)).isEqualTo(4 * ONE_MILLION);
            ProsperityAccount account = accountRepository.findByCommercialId(commercial.getId()).orElseThrow();
            assertThat(thresholdRepository.findByAccountIdOrderByValidatedAtAsc(account.getId())).hasSize(1);
            assertThat(account.getLastSequence()).isEqualTo(1L);
        }

        @Test
        @DisplayName("inversión PREMIUM (sin multiplicador) no genera Umbral ni cuenta")
        void premiumInvestment_generatesNothing() {
            Plan premium = plan(PlanCode.PREMIUM, null);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            commercial.setCurrentPlan(premium);
            TestEntities.persistWallet(em, commercial, 0L);
            Investment investment = confirmedInvestment(commercial, premium, 10 * ONE_MILLION);

            assertThat(service.generateThreshold(investment)).isEmpty();
            assertThat(accountRepository.findByCommercialId(commercial.getId())).isEmpty();
        }

        @Test
        @DisplayName("inversión no confirmada: nunca genera Umbral")
        void unconfirmedInvestment_isRejected() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = confirmedInvestment(commercial, commercial.getCurrentPlan(), ONE_MILLION);
            investment.setConfirmed(false);

            assertThatThrownBy(() -> service.generateThreshold(investment))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ─── Absorción ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Absorción de Ventas Computables")
    class Absorption {

        @Test
        @DisplayName("consumo parcial, agotamiento exacto y venta con saldo en cero")
        void partial_exact_andZeroBalance() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION); // saldo 4M

            PurchaseItem partial = item(commercial, ONE_MILLION, 10);      // 4M → 3M
            PurchaseItem exact = item(commercial, 3 * ONE_MILLION, 10);    // 3M → 0
            PurchaseItem uncovered = item(commercial, 50_000_000L, 10);    // saldo 0: paga todo
            Purchase purchase = purchase(partial, exact, uncovered);

            service.absorbPurchase(purchase);

            assertThat(partial.getProsperityAbsorbedCents()).isEqualTo(ONE_MILLION);
            assertThat(partial.getCommissionCents()).isZero();
            assertThat(partial.getNetToCommercialCents()).isEqualTo(ONE_MILLION);

            assertThat(exact.getProsperityAbsorbedCents()).isEqualTo(3 * ONE_MILLION);
            assertThat(exact.getCommissionBaseCents()).isZero();
            assertThat(exact.getCommissionCents()).isZero();

            assertThat(uncovered.getProsperityAbsorbedCents()).isZero();
            assertThat(uncovered.getCommissionBaseCents()).isEqualTo(50_000_000L);
            assertThat(uncovered.getCommissionCents()).isEqualTo(5_000_000L);
            assertThat(uncovered.getCommissionVatCents()).isEqualTo(950_000L);

            assertThat(balance(commercial)).isZero();
            assertThat(purchase.getProsperityAbsorbedCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(purchase.getCommissionCents()).isEqualTo(5_000_000L);
            assertThat(purchase.getNetToCommercialsCents())
                    .isEqualTo(purchase.getTotalCents() - purchase.getCommissionCents());
        }

        @Test
        @DisplayName("venta superior al saldo: absorbe lo que alcanza, saldo a cero, el excedente es Base (10.13)")
        void saleAboveBalance_excessIsCommissionable() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION); // saldo 4M

            PurchaseItem sale = item(commercial, 10 * ONE_MILLION, 15); // SERVICIOS 15%
            service.absorbPurchase(purchase(sale));

            assertThat(sale.getProsperityAbsorbedCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(sale.getCommissionBaseCents()).isEqualTo(6 * ONE_MILLION);
            assertThat(sale.getCommissionCents()).isEqualTo(90_000_000L); // 15% de 6M
            assertThat(balance(commercial)).isZero();

            ProsperityLedgerEntry absorption = ledgerRepository
                    .findByIdempotencyKey("ABSORPTION:ITEM:" + sale.getId()).orElseThrow();
            assertThat(absorption.getSaleAmountCents()).isEqualTo(10 * ONE_MILLION);
            assertThat(absorption.getBalanceAfterCents()).isZero();
        }

        @Test
        @DisplayName("venta que supera el saldo por $1: absorbe todo el saldo y la Base es $1")
        void saleAboveBalanceByOnePeso() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION);

            PurchaseItem sale = item(commercial, 4 * ONE_MILLION + 100, 10);
            service.absorbPurchase(purchase(sale));

            assertThat(sale.getProsperityAbsorbedCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(sale.getCommissionBaseCents()).isEqualTo(100L);
            assertThat(sale.getCommissionCents()).isEqualTo(10L);
        }

        @Test
        @DisplayName("comercial sin cuenta (BASIC): comisión completa, sin tocar el libro")
        void commercialWithoutAccount_paysFullCommission() {
            Plan basic = plan(PlanCode.BASIC, null);
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            commercial.setCurrentPlan(basic);
            em.flush();

            PurchaseItem sale = item(commercial, ONE_MILLION, 20);
            service.absorbPurchase(purchase(sale));

            assertThat(sale.getProsperityAbsorbedCents()).isZero();
            assertThat(sale.getCommissionCents()).isEqualTo(20_000_000L);
            assertThat(sale.getCommissionBaseCents()).isEqualTo(ONE_MILLION);
        }

        @Test
        @DisplayName("congelado: si deja de ser STANDARD conserva el saldo sin absorber; al volver lo reactiva")
        void frozenWhileNotStandard_resumesOnReturn() {
            CommercialDetails commercial = standardCommercial();
            Plan standard = commercial.getCurrentPlan();
            invest(commercial, ONE_MILLION);

            commercial.setCurrentPlan(plan(PlanCode.BASIC, null));
            em.flush();

            PurchaseItem whileBasic = item(commercial, ONE_MILLION, 20);
            service.absorbPurchase(purchase(whileBasic));
            assertThat(whileBasic.getProsperityAbsorbedCents()).isZero();
            assertThat(balance(commercial)).isEqualTo(4 * ONE_MILLION);
            assertThat(service.getSummary(commercial.getId()).status()).isEqualTo("FROZEN");

            commercial.setCurrentPlan(standard);
            em.flush();

            PurchaseItem backOnStandard = item(commercial, ONE_MILLION, 10);
            service.absorbPurchase(purchase(backOnStandard));
            assertThat(backOnStandard.getProsperityAbsorbedCents()).isEqualTo(ONE_MILLION);
            assertThat(balance(commercial)).isEqualTo(3 * ONE_MILLION);
            assertThat(service.getSummary(commercial.getId()).status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("una inversión posterior no absorbe una venta ya comisionada (orden cronológico, 10.4)")
        void laterInvestment_neverAppliesToEarlierSale() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION);
            PurchaseItem drain = item(commercial, 4 * ONE_MILLION, 10);
            service.absorbPurchase(purchase(drain));

            PurchaseItem commissioned = item(commercial, ONE_MILLION, 10);
            service.absorbPurchase(purchase(commissioned));
            assertThat(commissioned.getCommissionCents()).isEqualTo(10_000_000L);

            invest(commercial, ONE_MILLION);

            assertThat(commissioned.getCommissionCents()).isEqualTo(10_000_000L);
            assertThat(balance(commercial)).isEqualTo(4 * ONE_MILLION);
        }
    }

    // ─── Reintegros, reversiones y ajustes ────────────────────────────────────

    @Nested
    @DisplayName("Reintegros, reversiones y ajustes")
    class Corrections {

        @Test
        @DisplayName("reembolso: devuelve al saldo lo absorbido, vinculado a la absorción; el doble reintegro es idempotente")
        void refund_reintegratesAbsorbedPortionOnce() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION);
            PurchaseItem sale = item(commercial, 5 * ONE_MILLION, 10); // absorbe 4M, base 1M
            service.absorbPurchase(purchase(sale));

            service.reintegrateRefund(sale);
            service.reintegrateRefund(sale);

            assertThat(balance(commercial)).isEqualTo(4 * ONE_MILLION);
            ProsperityLedgerEntry reintegration = ledgerRepository
                    .findByIdempotencyKey("REINTEGRATION:ITEM:" + sale.getId()).orElseThrow();
            assertThat(reintegration.getAmountCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(reintegration.getRelatedEntry().getIdempotencyKey())
                    .isEqualTo("ABSORPTION:ITEM:" + sale.getId());
        }

        @Test
        @DisplayName("reembolso de un ítem sin absorción: no toca el libro")
        void refundWithoutAbsorption_isNoop() {
            CommercialDetails commercial = standardCommercial();
            PurchaseItem sale = item(commercial, ONE_MILLION, 10);
            sale.settleCommission(0L, 19);

            service.reintegrateRefund(sale);

            assertThat(accountRepository.findByCommercialId(commercial.getId())).isEmpty();
        }

        @Test
        @DisplayName("reverso de inversión no usada: retira el Umbral completo")
        void reversal_unusedThreshold() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = invest(commercial, ONE_MILLION);

            ProsperityMovementResponseDTO result = service.reverseThreshold(investment.getId(),
                    new ProsperityReversalRequestDTO("Pago reembolsado por Wompi", "nota crédito NC-1"), 99L);

            assertThat(result.amountCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(result.uncoveredCents()).isZero();
            assertThat(balance(commercial)).isZero();
            assertThat(accountRepository.findByCommercialId(commercial.getId()).orElseThrow()
                    .getAccumulatedThresholdCents()).isZero();
        }

        @Test
        @DisplayName("reverso de inversión ya usada en parte: retira el remanente y registra lo no cubierto, sin saldo negativo")
        void reversal_partiallyUsedThreshold() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = invest(commercial, ONE_MILLION);
            service.absorbPurchase(purchase(item(commercial, 3 * ONE_MILLION, 10))); // saldo 1M

            ProsperityMovementResponseDTO result = service.reverseThreshold(investment.getId(),
                    new ProsperityReversalRequestDTO("Contracargo", null), 99L);

            assertThat(result.amountCents()).isEqualTo(ONE_MILLION);
            assertThat(result.uncoveredCents()).isEqualTo(3 * ONE_MILLION);
            assertThat(balance(commercial)).isZero();
        }

        @Test
        @DisplayName("reverso de inversión consumida por completo: asiento en cero con todo como no cubierto")
        void reversal_fullyUsedThreshold() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = invest(commercial, ONE_MILLION);
            service.absorbPurchase(purchase(item(commercial, 4 * ONE_MILLION, 10)));

            ProsperityMovementResponseDTO result = service.reverseThreshold(investment.getId(),
                    new ProsperityReversalRequestDTO("Contracargo", null), 99L);

            assertThat(result.amountCents()).isZero();
            assertThat(result.uncoveredCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(balance(commercial)).isZero();
        }

        @Test
        @DisplayName("reverso usa el multiplicador histórico aunque la feature del plan cambie después; es idempotente")
        void reversal_usesHistoricalMultiplier_andIsIdempotent() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = invest(commercial, ONE_MILLION); // x4

            commercial.getCurrentPlan().getFeatures().stream()
                    .filter(pf -> pf.getFeature().getCode().equals(ProsperityServiceImpl.MULTIPLIER_FEATURE))
                    .findFirst().orElseThrow().setIntValue(2);
            em.flush();

            var request = new ProsperityReversalRequestDTO("Anulación", null);
            ProsperityMovementResponseDTO first = service.reverseThreshold(investment.getId(), request, 99L);
            ProsperityMovementResponseDTO second = service.reverseThreshold(investment.getId(), request, 99L);

            assertThat(first.amountCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(second.id()).isEqualTo(first.id());
            assertThat(balance(commercial)).isZero();
        }

        @Test
        @DisplayName("ajuste compensatorio: requiere causal y nunca deja el saldo negativo")
        void adjustment_requiresCause_andCannotGoNegative() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION);

            assertThatThrownBy(() -> service.adjust(commercial.getId(),
                    new ProsperityAdjustmentRequestDTO(true, 1_000L, " ", null, null, null), 99L))
                    .isInstanceOf(BusinessException.class);

            assertThatThrownBy(() -> service.adjust(commercial.getId(),
                    new ProsperityAdjustmentRequestDTO(false, 5 * ONE_MILLION, "error de base", null, null, null), 99L))
                    .isInstanceOf(BusinessException.class);

            ProsperityMovementResponseDTO credit = service.adjust(commercial.getId(),
                    new ProsperityAdjustmentRequestDTO(true, 500_000L, "error de $5.000 en Base", "ticket 42",
                            null, "adj-1"), 99L);
            ProsperityMovementResponseDTO retried = service.adjust(commercial.getId(),
                    new ProsperityAdjustmentRequestDTO(true, 500_000L, "error de $5.000 en Base", "ticket 42",
                            null, "adj-1"), 99L);

            assertThat(credit.balanceAfterCents()).isEqualTo(4 * ONE_MILLION + 500_000L);
            assertThat(retried.id()).isEqualTo(credit.id());
            assertThat(credit.performedBy()).isEqualTo("ADMIN:99");
        }
    }

    // ─── Libro y conciliación ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Libro mayor y conciliación")
    class Ledger {

        @Test
        @DisplayName("reconstrucción: cada asiento encadena saldo antes/después y la conciliación no encuentra descuadres")
        void ledgerChains_andReconciles() {
            CommercialDetails commercial = standardCommercial();
            Investment first = invest(commercial, ONE_MILLION);
            PurchaseItem sale = item(commercial, 2 * ONE_MILLION, 10);
            service.absorbPurchase(purchase(sale));
            service.reintegrateRefund(sale);
            invest(commercial, 2 * ONE_MILLION);
            service.reverseThreshold(first.getId(), new ProsperityReversalRequestDTO("anulación", null), 1L);

            List<ProsperityMovementResponseDTO> movements = service
                    .getMovements(commercial.getId(), PageRequest.of(0, 50)).getContent();
            assertThat(movements).hasSize(5);
            for (int i = 0; i < movements.size() - 1; i++) {
                // más reciente primero: el "antes" de cada asiento es el "después" del anterior
                assertThat(movements.get(i).balanceBeforeCents()).isEqualTo(movements.get(i + 1).balanceAfterCents());
            }
            assertThat(movements.get(movements.size() - 1).balanceBeforeCents()).isZero();

            assertThat(service.reconcile().discrepancies()).isEmpty();

            ProsperitySummaryResponseDTO summary = service.getSummary(commercial.getId());
            assertThat(summary.balanceCents()).isEqualTo(8 * ONE_MILLION); // 4 − 2 + 2 + 8 − 4
            assertThat(summary.accumulatedThresholdCents()).isEqualTo(8 * ONE_MILLION);
            assertThat(summary.thresholds()).hasSize(2);
            assertThat(summary.disclaimer()).contains("no es dinero");
        }

        @Test
        @DisplayName("intento de reinicio: el libro es inmutable, un UPDATE sobre un asiento no persiste")
        void ledgerEntries_cannotBeRewritten() {
            CommercialDetails commercial = standardCommercial();
            Investment investment = invest(commercial, ONE_MILLION);
            em.flush();
            em.clear();

            ProsperityLedgerEntry entry = ledgerRepository
                    .findByIdempotencyKey("THRESHOLD:INV:" + investment.getId()).orElseThrow();
            org.springframework.test.util.ReflectionTestUtils.setField(entry, "amountCents", 1L);
            org.springframework.test.util.ReflectionTestUtils.setField(entry, "balanceAfterCents", 0L);
            em.flush();
            em.clear();

            ProsperityLedgerEntry reloaded = ledgerRepository.findById(entry.getId()).orElseThrow();
            assertThat(reloaded.getAmountCents()).isEqualTo(4 * ONE_MILLION);
            assertThat(reloaded.getBalanceAfterCents()).isEqualTo(4 * ONE_MILLION);
        }

        @Test
        @DisplayName("la conciliación detecta un saldo que no coincide con el libro")
        void reconciliation_detectsTamperedBalance() {
            CommercialDetails commercial = standardCommercial();
            invest(commercial, ONE_MILLION);
            em.flush();

            em.createNativeQuery("UPDATE prosperity_accounts SET balance_cents = 1 WHERE commercial_id = :id")
                    .setParameter("id", commercial.getId())
                    .executeUpdate();
            em.clear();

            assertThat(service.reconcile().discrepancies()).isNotEmpty();
        }

        @Test
        @DisplayName("resumen de un comercial STANDARD sin inversiones: ACTIVE en cero; BASIC sin cuenta: NOT_APPLICABLE")
        void summaryWithoutAccount() {
            CommercialDetails standard = standardCommercial();
            Plan basic = plan(PlanCode.BASIC, null);
            CommercialDetails basicCommercial = TestEntities.persistCommercial(em);
            basicCommercial.setCurrentPlan(basic);
            em.flush();

            assertThat(service.getSummary(standard.getId()).status()).isEqualTo("ACTIVE");
            assertThat(service.getSummary(standard.getId()).balanceCents()).isZero();
            assertThat(service.getSummary(basicCommercial.getId()).status()).isEqualTo("NOT_APPLICABLE");
        }
    }

    // ─── Concurrencia ─────────────────────────────────────────────────────────

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("concurrencia: dos compras de $3M contra un saldo de $4M nunca absorben más de $4M")
    void concurrentPurchases_neverDoubleSpendBalance() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        CommercialDetails commercial = tx.execute(status -> {
            CommercialDetails c = standardCommercial();
            invest(c, ONE_MILLION);
            return c;
        });

        PurchaseItem first = item(commercial, 3 * ONE_MILLION, 10);
        PurchaseItem second = item(commercial, 3 * ONE_MILLION, 10);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Void> absorbFirst = () -> {
            start.await();
            service.absorbPurchase(purchase(first));
            return null;
        };
        Callable<Void> absorbSecond = () -> {
            start.await();
            service.absorbPurchase(purchase(second));
            return null;
        };
        Future<Void> f1 = pool.submit(absorbFirst);
        Future<Void> f2 = pool.submit(absorbSecond);
        start.countDown();
        f1.get(20, TimeUnit.SECONDS);
        f2.get(20, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(first.getProsperityAbsorbedCents() + second.getProsperityAbsorbedCents())
                .isEqualTo(4 * ONE_MILLION);
        assertThat(List.of(first.getProsperityAbsorbedCents(), second.getProsperityAbsorbedCents()))
                .containsExactlyInAnyOrder(3 * ONE_MILLION, ONE_MILLION);

        Long balance = tx.execute(status -> balance(commercial));
        assertThat(balance).isZero();
        List<String> discrepancies = tx.execute(status -> service.reconcile().discrepancies());
        assertThat(discrepancies).isEmpty();
    }
}
