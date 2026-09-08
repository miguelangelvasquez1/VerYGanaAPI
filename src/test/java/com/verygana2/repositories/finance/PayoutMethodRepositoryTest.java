package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.finance.PayoutMethod;
import com.verygana2.models.finance.PayoutMethod.VerificationStatus;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PayoutMethodRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:payout-method-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PayoutMethodRepository (integración H2)")
class PayoutMethodRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PayoutMethodRepository payoutMethodRepository;

    // ==================== HELPERS ====================

    private void setActive(PayoutMethod method, boolean active) {
        method.setActive(active);
        em.flush();
    }

    // ==================== findByCommercialId ====================

    @Nested
    @DisplayName("findByCommercialId")
    class FindByCommercialId {

        @Test
        @DisplayName("trae solo los métodos del commercial indicado")
        void returnsOnlyMethodsOfGivenCommercial() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            PayoutMethod ownMethod = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.PENDING_VERIFICATION);
            TestEntities.persistPayoutMethod(em, other, VerificationStatus.PENDING_VERIFICATION);

            Page<PayoutMethod> page = payoutMethodRepository.findByCommercialId(commercial.getId(),
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PayoutMethod::getId).containsExactly(ownMethod.getId());
        }

        @Test
        @DisplayName("retorna página vacía si el commercial no tiene métodos")
        void returnsEmptyPageWhenCommercialHasNoMethods() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);

            Page<PayoutMethod> page = payoutMethodRepository.findByCommercialId(commercial.getId(),
                    PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
        }
    }

    // ==================== findByIdAndCommercialId ====================

    @Nested
    @DisplayName("findByIdAndCommercialId")
    class FindByIdAndCommercialId {

        @Test
        @DisplayName("solo retorna el método si pertenece al commercial dado (ownership)")
        void onlyReturnsMethodWhenOwnedByCommercial() {
            CommercialDetails owner = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            PayoutMethod method = TestEntities.persistPayoutMethod(em, owner, VerificationStatus.VERIFIED);

            Optional<PayoutMethod> found = payoutMethodRepository.findByIdAndCommercialId(method.getId(),
                    owner.getId());
            Optional<PayoutMethod> notFound = payoutMethodRepository.findByIdAndCommercialId(method.getId(),
                    other.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(method.getId());
            assertThat(notFound).isEmpty();
        }
    }

    // ==================== findByVerificationStatus ====================

    @Nested
    @DisplayName("findByVerificationStatus")
    class FindByVerificationStatus {

        @Test
        @DisplayName("trae solo los métodos con el status de verificación indicado")
        void returnsOnlyMethodsWithGivenStatus() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod underReview = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.UNDER_REVIEW);
            TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.VERIFIED);
            TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.REJECTED);

            Page<PayoutMethod> page = payoutMethodRepository.findByVerificationStatus(
                    VerificationStatus.UNDER_REVIEW, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(PayoutMethod::getId).containsExactly(underReview.getId());
        }
    }

    // ==================== findFirstByCommercialIdAndVerificationStatusAndActiveTrue ====================

    @Nested
    @DisplayName("findFirstByCommercialIdAndVerificationStatusAndActiveTrue")
    class FindFirstByCommercialIdAndVerificationStatusAndActiveTrue {

        @Test
        @DisplayName("ignora métodos inactivos o no VERIFIED, y encuentra el VERIFIED+activo")
        void ignoresInactiveOrUnverifiedMethods() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod verifiedActive = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.VERIFIED);
            PayoutMethod verifiedInactive = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.VERIFIED);
            setActive(verifiedInactive, false);
            TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.UNDER_REVIEW);

            Optional<PayoutMethod> found = payoutMethodRepository
                    .findFirstByCommercialIdAndVerificationStatusAndActiveTrue(commercial.getId(),
                            VerificationStatus.VERIFIED);

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(verifiedActive.getId());
        }

        @Test
        @DisplayName("si hay varios VERIFIED+activos, retorna uno de ellos")
        void returnsFirstWhenSeveralVerifiedAndActive() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod first = TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.VERIFIED);
            PayoutMethod second = TestEntities.persistPayoutMethod(em, commercial, VerificationStatus.VERIFIED);

            Optional<PayoutMethod> found = payoutMethodRepository
                    .findFirstByCommercialIdAndVerificationStatusAndActiveTrue(commercial.getId(),
                            VerificationStatus.VERIFIED);

            assertThat(found).isPresent();
            assertThat(found.get().isActive()).isTrue();
            assertThat(found.get().getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
            assertThat(List.of(first.getId(), second.getId())).contains(found.get().getId());
        }

        @Test
        @DisplayName("vacío cuando el commercial no tiene métodos VERIFIED+activos")
        void emptyWhenNoVerifiedAndActiveMethods() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PayoutMethod verifiedInactive = TestEntities.persistPayoutMethod(em, commercial,
                    VerificationStatus.VERIFIED);
            setActive(verifiedInactive, false);

            Optional<PayoutMethod> found = payoutMethodRepository
                    .findFirstByCommercialIdAndVerificationStatusAndActiveTrue(commercial.getId(),
                            VerificationStatus.VERIFIED);

            assertThat(found).isEmpty();
        }
    }
}
