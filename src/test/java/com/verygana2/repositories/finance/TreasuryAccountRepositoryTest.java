package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para TreasuryAccountRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:treasury-account-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TreasuryAccountRepository (integración H2)")
class TreasuryAccountRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private TreasuryAccountRepository treasuryAccountRepository;

    // ==================== findByCode / existsByCode ====================

    @Nested
    @DisplayName("findByCode y existsByCode")
    class FindAndExistsByCode {

        @Test
        @DisplayName("encuentra la cuenta exacta por code y no encuentra un code sin cuenta creada")
        void findsExactAccountByCodeAndEmptyForMissingCode() {
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE, 5000L);

            Optional<TreasuryAccount> found = treasuryAccountRepository.findByCode(TreasuryAccountCode.KEYS_RESERVE);
            Optional<TreasuryAccount> notFound = treasuryAccountRepository.findByCode(TreasuryAccountCode.OPERATIONS);

            assertThat(found).isPresent();
            assertThat(found.get().getBalanceCents()).isEqualTo(5000L);
            assertThat(notFound).isEmpty();

            assertThat(treasuryAccountRepository.existsByCode(TreasuryAccountCode.KEYS_RESERVE)).isTrue();
            assertThat(treasuryAccountRepository.existsByCode(TreasuryAccountCode.PAYOUTS_PENDING)).isFalse();
        }
    }

    // ==================== findByCodeForUpdate ====================

    @Nested
    @DisplayName("findByCodeForUpdate")
    class FindByCodeForUpdate {

        @Test
        @DisplayName("retorna la cuenta correcta bajo lock PESSIMISTIC_WRITE dentro de la transacción del test")
        void returnsCorrectAccountUnderPessimisticWriteLock() {
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.FORTIFICATION, 1500L);
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.OPERATIONS, 2500L);

            Optional<TreasuryAccount> locked = treasuryAccountRepository
                    .findByCodeForUpdate(TreasuryAccountCode.FORTIFICATION);

            assertThat(locked).isPresent();
            assertThat(locked.get().getCode()).isEqualTo(TreasuryAccountCode.FORTIFICATION);
            assertThat(locked.get().getBalanceCents()).isEqualTo(1500L);
        }
    }

    // ==================== countNegativeBalances ====================

    @Nested
    @DisplayName("countNegativeBalances")
    class CountNegativeBalances {

        @Test
        @DisplayName("retorna 0 cuando ninguna cuenta tiene saldo negativo")
        void returnsZeroWhenNoNegativeBalances() {
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE, 1000L);
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.OPERATIONS, 0L);

            assertThat(treasuryAccountRepository.countNegativeBalances()).isZero();
        }

        @Test
        @DisplayName("cuenta solo las cuentas con balanceCents negativo")
        void countsOnlyAccountsWithNegativeBalance() {
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE, -100L);
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.FORTIFICATION, -50L);
            TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.OPERATIONS, 200L);

            assertThat(treasuryAccountRepository.countNegativeBalances()).isEqualTo(2L);
        }
    }
}
