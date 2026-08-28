package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.enums.finance.MovementConcept;
import com.verygana2.models.enums.finance.TreasuryAccountCode;
import com.verygana2.models.finance.TreasuryAccount;
import com.verygana2.models.finance.TreasuryMovement;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para TreasuryMovementRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:treasury-movement-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TreasuryMovementRepository (integración H2)")
class TreasuryMovementRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private TreasuryMovementRepository treasuryMovementRepository;

    // ==================== HELPERS ====================

    private TreasuryMovement persistMovement(TreasuryAccount from, TreasuryAccount to, long amountCents,
            MovementConcept concept, UUID referenceId, String referenceType) {
        TreasuryMovement movement = TreasuryMovement.builder()
                .fromAccount(from)
                .toAccount(to)
                .amountCents(amountCents)
                .concept(concept)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .build();
        em.persist(movement);
        em.flush();
        return movement;
    }

    // ==================== findByReferenceIdAndReferenceType ====================

    @Nested
    @DisplayName("findByReferenceIdAndReferenceType")
    class FindByReferenceIdAndReferenceType {

        @Test
        @DisplayName("trae solo los movimientos que coinciden exactamente en referenceId y referenceType")
        void returnsOnlyExactReferenceMatch() {
            TreasuryAccount reserve = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE,
                    10000L);
            TreasuryAccount pending = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.PAYOUTS_PENDING,
                    0L);

            UUID targetRef = UUID.randomUUID();
            TreasuryMovement match = persistMovement(reserve, pending, 500L,
                    MovementConcept.COPAYMENT_KEYS_CONVERSION, targetRef, "Copayment");
            // Mismo referenceId pero otro referenceType: no debe coincidir.
            persistMovement(reserve, pending, 300L, MovementConcept.COPAYMENT_KEYS_CONVERSION, targetRef, "Purchase");
            // Otro referenceId: no debe coincidir.
            persistMovement(reserve, pending, 200L, MovementConcept.COPAYMENT_KEYS_CONVERSION, UUID.randomUUID(),
                    "Copayment");

            List<TreasuryMovement> result = treasuryMovementRepository.findByReferenceIdAndReferenceType(targetRef,
                    "Copayment");

            assertThat(result).extracting(TreasuryMovement::getId).containsExactly(match.getId());
        }
    }

    // ==================== findByConcept ====================

    @Nested
    @DisplayName("findByConcept")
    class FindByConcept {

        @Test
        @DisplayName("filtra los movimientos por concept")
        void filtersMovementsByConcept() {
            TreasuryAccount reserve = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE,
                    10000L);
            TreasuryAccount operations = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.OPERATIONS, 0L);

            TreasuryMovement commission = persistMovement(reserve, operations, 100L,
                    MovementConcept.COMMISSION_RETENTION, UUID.randomUUID(), "Purchase");
            persistMovement(reserve, operations, 200L, MovementConcept.SALE_TO_PAYOUT_PENDING, UUID.randomUUID(),
                    "Purchase");

            List<TreasuryMovement> result = treasuryMovementRepository.findByConcept(
                    MovementConcept.COMMISSION_RETENTION);

            assertThat(result).extracting(TreasuryMovement::getId).containsExactly(commission.getId());
        }
    }

    // ==================== findByAccountCode ====================

    @Nested
    @DisplayName("findByAccountCode")
    class FindByAccountCode {

        @Test
        @DisplayName("trae movimientos donde la cuenta es origen O destino, y excluye movimientos de otras cuentas")
        void returnsMovementsWhereAccountIsFromOrTo() {
            TreasuryAccount reserve = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.KEYS_RESERVE,
                    10000L);
            TreasuryAccount pending = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.PAYOUTS_PENDING,
                    0L);
            TreasuryAccount operations = TestEntities.persistTreasuryAccount(em, TreasuryAccountCode.OPERATIONS, 0L);

            // reserve es origen.
            TreasuryMovement asOrigin = persistMovement(reserve, pending, 500L,
                    MovementConcept.COPAYMENT_KEYS_CONVERSION, UUID.randomUUID(), "Copayment");
            // reserve es destino.
            TreasuryMovement asDestination = persistMovement(operations, reserve, 300L,
                    MovementConcept.EXPIRED_KEYS_TO_FORTIFICATION, UUID.randomUUID(), "KeyExpiryBatch");
            // No involucra a reserve en absoluto: no debe incluirse.
            persistMovement(pending, operations, 100L, MovementConcept.COMMISSION_RETENTION, UUID.randomUUID(),
                    "Purchase");

            Page<TreasuryMovement> page = treasuryMovementRepository.findByAccountCode(
                    TreasuryAccountCode.KEYS_RESERVE, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).extracting(TreasuryMovement::getId)
                    .containsExactlyInAnyOrder(asOrigin.getId(), asDestination.getId());
        }
    }
}
