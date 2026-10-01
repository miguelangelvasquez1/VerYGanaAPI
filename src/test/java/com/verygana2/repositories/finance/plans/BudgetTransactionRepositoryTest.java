package com.verygana2.repositories.finance.plans;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.finance.Wallet;
import com.verygana2.models.finance.plans.BudgetTransaction;
import com.verygana2.models.finance.plans.BudgetTransaction.TransactionType;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para BudgetTransactionRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:budget-tx-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("BudgetTransactionRepository (integración H2)")
class BudgetTransactionRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private BudgetTransactionRepository budgetTransactionRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now(ZoneOffset.UTC);
    }

    // BudgetTransaction no tiene @PrePersist: createdAt se fija explícitamente
    // aquí, a diferencia de Investment/Subscription.
    private BudgetTransaction persistBudgetTransaction(Wallet wallet, long amountCents, TransactionType type,
            ZonedDateTime createdAt) {
        BudgetTransaction transaction = BudgetTransaction.builder()
                .wallet(wallet)
                .amountCents(amountCents)
                .type(type)
                .createdAt(createdAt)
                .build();
        em.persist(transaction);
        em.flush();
        return transaction;
    }

    // ==================== findByWalletIdAndType ====================

    @Nested
    @DisplayName("findByWalletIdAndType")
    class FindByWalletIdAndType {

        @Test
        @DisplayName("trae solo las transacciones del wallet que coinciden con el tipo")
        void bringsTransactionsMatchingWalletAndType() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 100_000_00L);

            BudgetTransaction adView = persistBudgetTransaction(wallet, 100_00L, TransactionType.AD_VIEW, now());
            persistBudgetTransaction(wallet, 200_00L, TransactionType.GAME_REWARD, now());

            List<BudgetTransaction> found = budgetTransactionRepository.findByWalletIdAndType(wallet.getId(),
                    TransactionType.AD_VIEW);

            assertThat(found).extracting(BudgetTransaction::getId).containsExactly(adView.getId());
        }

        @Test
        @DisplayName("no trae transacciones de otro wallet aunque coincidan en tipo")
        void doesNotBringTransactionsFromOtherWallet() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em);
            Wallet walletA = TestEntities.persistWallet(em, commercialA, 0L);
            CommercialDetails commercialB = TestEntities.persistCommercial(em);
            Wallet walletB = TestEntities.persistWallet(em, commercialB, 100_000_00L);

            persistBudgetTransaction(walletB, 100_00L, TransactionType.AD_VIEW, now());

            List<BudgetTransaction> found = budgetTransactionRepository.findByWalletIdAndType(walletA.getId(),
                    TransactionType.AD_VIEW);

            assertThat(found).isEmpty();
        }
    }

    // ==================== sumByWalletIdAndPeriod ====================

    @Nested
    @DisplayName("sumByWalletIdAndPeriod")
    class SumByWalletIdAndPeriod {

        @Test
        @DisplayName("suma el gasto publicitario dentro del período")
        void sumsSpendingWithinPeriod() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 100_000_00L);

            ZonedDateTime from = now().minusDays(10);
            ZonedDateTime to = now().minusDays(1);

            persistBudgetTransaction(wallet, 100_00L, TransactionType.AD_VIEW, now().minusDays(5));
            persistBudgetTransaction(wallet, 200_00L, TransactionType.GAME_REWARD, now().minusDays(3));
            // Fuera de rango: no debe sumar.
            persistBudgetTransaction(wallet, 999_00L, TransactionType.AD_VIEW, now().minusDays(20));

            BigDecimal sum = budgetTransactionRepository.sumByWalletIdAndPeriod(wallet.getId(), from, to);

            assertThat(sum).isEqualByComparingTo(BigDecimal.valueOf(300_00L));
        }

        @Test
        @DisplayName("retorna 0 (no null) cuando no hay transacciones en el período — usa COALESCE igual que InvestmentRepository.sumConfirmedByWalletIdAndPeriod")
        void returnsZeroNotNullWhenNoTransactionsInPeriod() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            Wallet wallet = TestEntities.persistWallet(em, commercial, 100_000_00L);

            BigDecimal sum = budgetTransactionRepository.sumByWalletIdAndPeriod(wallet.getId(), now().minusDays(10),
                    now().plusDays(1));

            assertThat(sum).isNotNull();
            assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }
}
