package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.enums.finance.WompiTransactionStatus;
import com.verygana2.models.enums.finance.WompiTransactionType;
import com.verygana2.models.finance.WompiTransaction;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para WompiTransactionRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:wompi-transaction-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("WompiTransactionRepository (integración H2)")
class WompiTransactionRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private WompiTransactionRepository wompiTransactionRepository;

    // ==================== HELPERS ====================

    private WompiTransaction persistTransaction(String wompiId, String reference) {
        WompiTransaction transaction = WompiTransaction.builder()
                .wompiId(wompiId)
                .type(WompiTransactionType.CHARGE_COPAYMENT)
                .amountInCents(50000L)
                .currency("COP")
                .status(WompiTransactionStatus.PENDING)
                .reference(reference)
                .wompiCreatedAt(ZonedDateTime.now(ZoneOffset.UTC))
                .build();
        em.persist(transaction);
        em.flush();
        return transaction;
    }

    // ==================== findByReference ====================

    @Nested
    @DisplayName("findByReference")
    class FindByReference {

        @Test
        @DisplayName("trae la transacción por la referencia interna enviada a Wompi")
        void returnsTransactionByReference() {
            WompiTransaction transaction = persistTransaction("WOMPI-ID-REF-1", "INTERNAL-REF-1");

            Optional<WompiTransaction> found = wompiTransactionRepository.findByReference("INTERNAL-REF-1");

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(transaction.getId());
        }

        @Test
        @DisplayName("vacío cuando no existe una transacción con esa referencia")
        void emptyWhenReferenceDoesNotExist() {
            Optional<WompiTransaction> found = wompiTransactionRepository.findByReference("NO-EXISTE-REF");

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByWompiId ====================

    @Nested
    @DisplayName("findByWompiId")
    class FindByWompiId {

        @Test
        @DisplayName("trae la transacción por el ID real de Wompi")
        void returnsTransactionByWompiId() {
            WompiTransaction transaction = persistTransaction("WOMPI-ID-2", "INTERNAL-REF-2");

            Optional<WompiTransaction> found = wompiTransactionRepository.findByWompiId("WOMPI-ID-2");

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(transaction.getId());
        }

        @Test
        @DisplayName("vacío cuando no existe una transacción con ese wompiId")
        void emptyWhenWompiIdDoesNotExist() {
            Optional<WompiTransaction> found = wompiTransactionRepository.findByWompiId("NO-EXISTE-WOMPI-ID");

            assertThat(found).isEmpty();
        }
    }
}
