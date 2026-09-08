package com.verygana2.repositories.commercial;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.verygana2.models.commercial.CommercialContract;
import com.verygana2.models.commercial.PlanChangeRequest;
import com.verygana2.models.enums.commercial.ContractPurpose;
import com.verygana2.models.enums.commercial.ContractStatus;
import com.verygana2.models.enums.finance.plans.PlanChangeRequestStatus;
import com.verygana2.models.finance.plans.Plan;
import com.verygana2.models.finance.plans.Plan.PlanCode;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PlanChangeRequestRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:plan-change-request-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PlanChangeRequestRepository (integración H2)")
class PlanChangeRequestRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private PlanChangeRequestRepository planChangeRequestRepository;

    private final AtomicInteger seq = new AtomicInteger(1);

    // Estados terminales usados típicamente para excluir en las consultas.
    private static final List<PlanChangeRequestStatus> TERMINAL_STATUSES = List.of(
            PlanChangeRequestStatus.APPLIED,
            PlanChangeRequestStatus.REJECTED,
            PlanChangeRequestStatus.CANCELLED);

    // ==================== HELPERS ====================

    private PlanChangeRequest persistPlanChangeRequest(CommercialDetails commercial, Plan fromPlan, Plan toPlan,
            PlanChangeRequestStatus status, CommercialContract contract) {
        PlanChangeRequest request = new PlanChangeRequest();
        request.setCommercial(commercial);
        request.setFromPlan(fromPlan);
        request.setToPlan(toPlan);
        request.setStatus(status);
        request.setContract(contract);
        em.persist(request);
        em.flush();
        return request;
    }

    private CommercialContract persistContract(CommercialDetails commercial) {
        long n = seq.getAndIncrement();
        CommercialContract contract = new CommercialContract();
        contract.setCommercial(commercial);
        contract.setPurpose(ContractPurpose.PLAN_CHANGE);
        contract.setObjectKey("contracts/plan-change-test-" + n + ".pdf");
        contract.setStatus(ContractStatus.PENDING_BUSINESS_REVIEW);
        em.persist(contract);
        em.flush();
        return contract;
    }

    // ==================== findByCommercial_IdAndStatusNotIn ====================

    @Nested
    @DisplayName("findByCommercial_IdAndStatusNotIn")
    class FindByCommercialIdAndStatusNotIn {

        @Test
        @DisplayName("excluye los estados terminales pasados y trae los no-terminales del comercial")
        void excludesTerminalStatusesAndBringsNonTerminal() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Plan basic = commercial.getCurrentPlan();
            CommercialDetails standardCommercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Plan standard = standardCommercial.getCurrentPlan();

            PlanChangeRequest requested = persistPlanChangeRequest(commercial, basic, standard,
                    PlanChangeRequestStatus.REQUESTED, null);
            persistPlanChangeRequest(commercial, basic, standard, PlanChangeRequestStatus.APPLIED, null);
            persistPlanChangeRequest(commercial, basic, standard, PlanChangeRequestStatus.REJECTED, null);
            persistPlanChangeRequest(commercial, basic, standard, PlanChangeRequestStatus.CANCELLED, null);

            List<PlanChangeRequest> found = planChangeRequestRepository
                    .findByCommercial_IdAndStatusNotIn(commercial.getId(), TERMINAL_STATUSES);

            assertThat(found).extracting(PlanChangeRequest::getId).containsExactly(requested.getId());
        }

        @Test
        @DisplayName("no trae solicitudes de otro comercial")
        void doesNotBringRequestsFromOtherCommercial() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Plan basic = commercialA.getCurrentPlan();
            CommercialDetails commercialB = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Plan standard = commercialB.getCurrentPlan();

            persistPlanChangeRequest(commercialB, basic, standard, PlanChangeRequestStatus.REQUESTED, null);

            List<PlanChangeRequest> found = planChangeRequestRepository
                    .findByCommercial_IdAndStatusNotIn(commercialA.getId(), TERMINAL_STATUSES);

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByStatusNotIn ====================

    @Nested
    @DisplayName("findByStatusNotIn")
    class FindByStatusNotIn {

        @Test
        @DisplayName("trae las solicitudes de todos los comerciales cuyo estado no está en la lista")
        void bringsRequestsFromAllCommercialsWithNonExcludedStatus() {
            CommercialDetails commercialA = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Plan basic = commercialA.getCurrentPlan();
            CommercialDetails commercialB = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Plan standard = commercialB.getCurrentPlan();

            PlanChangeRequest pendingA = persistPlanChangeRequest(commercialA, basic, standard,
                    PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW, null);
            PlanChangeRequest pendingB = persistPlanChangeRequest(commercialB, standard, basic,
                    PlanChangeRequestStatus.PAYMENT_PENDING, null);
            persistPlanChangeRequest(commercialA, basic, standard, PlanChangeRequestStatus.APPLIED, null);
            persistPlanChangeRequest(commercialB, standard, basic, PlanChangeRequestStatus.CANCELLED, null);

            List<PlanChangeRequest> found = planChangeRequestRepository.findByStatusNotIn(TERMINAL_STATUSES);

            assertThat(found).extracting(PlanChangeRequest::getId)
                    .containsExactlyInAnyOrder(pendingA.getId(), pendingB.getId());
        }

        @Test
        @DisplayName("retorna lista vacía si todas las solicitudes están en estados excluidos")
        void returnsEmptyListWhenAllRequestsAreExcluded() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Plan basic = commercial.getCurrentPlan();
            CommercialDetails standardCommercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Plan standard = standardCommercial.getCurrentPlan();

            persistPlanChangeRequest(commercial, basic, standard, PlanChangeRequestStatus.APPLIED, null);

            List<PlanChangeRequest> found = planChangeRequestRepository.findByStatusNotIn(TERMINAL_STATUSES);

            assertThat(found).isEmpty();
        }
    }

    // ==================== findByContract_Id ====================

    @Nested
    @DisplayName("findByContract_Id")
    class FindByContractId {

        @Test
        @DisplayName("encuentra la solicitud vinculada al contrato")
        void findsRequestLinkedToContract() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            Plan basic = commercial.getCurrentPlan();
            CommercialDetails standardCommercial = TestEntities.persistCommercial(em, PlanCode.STANDARD);
            Plan standard = standardCommercial.getCurrentPlan();

            CommercialContract contract = persistContract(commercial);
            PlanChangeRequest request = persistPlanChangeRequest(commercial, basic, standard,
                    PlanChangeRequestStatus.CONTRACT_PENDING_REVIEW, contract);

            Optional<PlanChangeRequest> found = planChangeRequestRepository.findByContract_Id(contract.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(request.getId());
        }

        @Test
        @DisplayName("retorna vacío si ninguna solicitud está vinculada a ese contrato")
        void returnsEmptyWhenNoRequestLinkedToContract() {
            CommercialDetails commercial = TestEntities.persistCommercial(em, PlanCode.BASIC);
            CommercialContract contract = persistContract(commercial);

            Optional<PlanChangeRequest> found = planChangeRequestRepository.findByContract_Id(contract.getId());

            assertThat(found).isEmpty();
        }
    }
}
