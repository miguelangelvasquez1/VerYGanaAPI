package com.verygana2.repositories.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.Investment;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para InvestmentRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:investment-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("InvestmentRepository (integración H2)")
class InvestmentRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private InvestmentRepository investmentRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    /**
     * Persiste un Investment. Investment.onCreate() (@PrePersist) siempre
     * pisa createdAt con "now" al insertar, y la columna es
     * updatable = false, así que un simple setter + flush no lo cambia en
     * BD. Para pruebas de período se sobreescribe con un UPDATE JPQL
     * explícito (bulk update), que sí ignora updatable = false.
     */
    private Investment persistInvestment(Wallet wallet, Plan plan, long amountCents, boolean confirmed,
            String wompiReference, ZonedDateTime createdAtOverride) {
        Investment investment = Investment.builder()
                .wallet(wallet)
                .planAtDeposit(plan)
                .depositAmountCents(amountCents)
                .confirmed(confirmed)
                .wompiReference(wompiReference)
                .build();
        em.persist(investment);
        em.flush();

        if (createdAtOverride != null) {
            em.createQuery("UPDATE Investment i SET i.createdAt = :val WHERE i.id = :id")
                    .setParameter("val", createdAtOverride)
                    .setParameter("id", investment.getId())
                    .executeUpdate();
        }
        return investment;
    }

    // ==================== findByWompiReference ====================

    @Nested
    @DisplayName("findByWompiReference")
    class FindByWompiReference {

        @Test
        @DisplayName("encuentra el investment por su referencia Wompi")
        void findsInvestmentByReference() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 0L);
            Investment investment = persistInvestment(wallet, commercial.getCurrentPlan(), 100_000_00L, false,
                    "VG-INV-123", null);

            Optional<Investment> found = investmentRepository.findByWompiReference("VG-INV-123");

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(investment.getId());
        }

        @Test
        @DisplayName("retorna vacío si no existe esa referencia")
        void returnsEmptyWhenReferenceDoesNotExist() {
            Optional<Investment> found = investmentRepository.findByWompiReference("NO-EXISTE");

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByWalletAndConfirmedTrue ====================

    @Nested
    @DisplayName("findByWalletAndConfirmedTrue")
    class FindByWalletAndConfirmedTrue {

        @Test
        @DisplayName("retorna solo los investments confirmados del wallet")
        void returnsOnlyConfirmedInvestmentsForWallet() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 0L);
            Investment confirmed = persistInvestment(wallet, commercial.getCurrentPlan(), 500_000_00L, true,
                    "VG-INV-CONF", null);
            persistInvestment(wallet, commercial.getCurrentPlan(), 300_000_00L, false, "VG-INV-PEND", null);

            List<Investment> found = investmentRepository.findByWalletAndConfirmedTrue(wallet);

            assertThat(found).extracting(Investment::getId).containsExactly(confirmed.getId());
        }

        @Test
        @DisplayName("no trae investments confirmados de otro wallet")
        void doesNotBringConfirmedInvestmentsFromOtherWallet() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Wallet walletA = TestEntities.persistWallet(em, commercialA, 0L);
            CommercialDetails commercialB = TestEntities.persistCommercial(em);
            Wallet walletB = TestEntities.persistWallet(em, commercialB, 0L);

            persistInvestment(walletB, commercialA.getCurrentPlan(), 500_000_00L, true, "VG-INV-B", null);

            List<Investment> found = investmentRepository.findByWalletAndConfirmedTrue(walletA);

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByWalletIdAndPeriod ====================

    @Nested
    @DisplayName("findByWalletIdAndPeriod")
    class FindByWalletIdAndPeriod {

        @Test
        @DisplayName("filtra por rango de fechas y ordena descendente por createdAt")
        void filtersByDateRangeOrderedDescending() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 0L);

            ZonedDateTime from = now().minusDays(10);
            ZonedDateTime to = now().minusDays(1);

            Investment older = persistInvestment(wallet, commercial.getCurrentPlan(), 100_00L, true, "VG-OLD",
                    now().minusDays(8));
            Investment newer = persistInvestment(wallet, commercial.getCurrentPlan(), 200_00L, true, "VG-NEW",
                    now().minusDays(3));
            persistInvestment(wallet, commercial.getCurrentPlan(), 300_00L, true, "VG-OUT", now().minusDays(20));

            List<Investment> found = investmentRepository.findByWalletIdAndPeriod(wallet.getId(), from, to);

            assertThat(found).extracting(Investment::getId).containsExactly(newer.getId(), older.getId());
        }
    }

    // ==================== sumConfirmedByWalletIdAndPeriod ====================

    @Nested
    @DisplayName("sumConfirmedByWalletIdAndPeriod")
    class SumConfirmedByWalletIdAndPeriod {

        @Test
        @DisplayName("suma solo los investments confirmados dentro del período")
        void sumsOnlyConfirmedInvestmentsInPeriod() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 0L);

            ZonedDateTime from = now().minusDays(10);
            ZonedDateTime to = now().minusDays(1);

            persistInvestment(wallet, commercial.getCurrentPlan(), 100_00L, true, "VG-C1", now().minusDays(5));
            persistInvestment(wallet, commercial.getCurrentPlan(), 200_00L, true, "VG-C2", now().minusDays(3));
            // No confirmado: no debe sumar.
            persistInvestment(wallet, commercial.getCurrentPlan(), 999_00L, false, "VG-PEND", now().minusDays(4));
            // Confirmado pero fuera de rango: no debe sumar.
            persistInvestment(wallet, commercial.getCurrentPlan(), 999_00L, true, "VG-OUT", now().minusDays(20));

            BigDecimal sum = investmentRepository.sumConfirmedByWalletIdAndPeriod(wallet.getId(), from, to);

            assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(300_00L));
        }

        @Test
        @DisplayName("retorna 0 (no null) cuando no hay investments confirmados en el período — a diferencia de las sumas de PayoutRepository/KeyTransactionRepository, esta usa COALESCE y nunca es null")
        void returnsZeroNotNullWhenNoConfirmedInvestments() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 0L);

            BigDecimal sum = investmentRepository.sumConfirmedByWalletIdAndPeriod(wallet.getId(),
                    now().minusDays(10), now().plusDays(1));

            assertThat(sum).isNotNull();
            assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
