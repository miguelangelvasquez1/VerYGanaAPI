package com.verygana2.repositories.pqrs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.User;
import com.verygana2.models.enums.Role;
import com.verygana2.models.enums.UserState;
import com.verygana2.models.enums.marketplace.PurchaseStatus;
import com.verygana2.models.enums.pqrs.PqrsStatus;
import com.verygana2.models.enums.pqrs.PqrsType;
import com.verygana2.models.marketplace.Product;
import com.verygana2.models.marketplace.ProductCategory;
import com.verygana2.models.marketplace.Purchase;
import com.verygana2.models.marketplace.PurchaseItem;
import com.verygana2.models.pqrs.Pqrs;
import com.verygana2.models.userDetails.AdminDetails;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para PqrsRepository. Cubre la
 * consulta de "mis PQRS" del solicitante, la bandeja de un admin con sus
 * filtros combinables, el derived query por status, la búsqueda de PQRS
 * vencidos usados por el job de escalamiento, y el chequeo de reclamo
 * abierto sobre un ítem de compra (usado para bloquear payouts/duplicados).
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:pqrs-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("PqrsRepository (integración H2)")
class PqrsRepositoryTest {

    private static final AtomicLong SEQ = new AtomicLong(1);

    @Autowired
    private EntityManager em;

    @Autowired
    private PqrsRepository pqrsRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    /** Construye y persiste un AdminDetails mínimo, siguiendo el mismo patrón que TestEntities.persistCommercial. */
    private AdminDetails persistAdmin(EntityManager em) {
        long n = SEQ.getAndIncrement();

        User user = new User();
        user.setEmail("admin" + n + "@test.com");
        user.setPhoneNumber("320000" + String.format("%04d", n));
        user.setPassword("hash");
        user.setRole(Role.ADMIN);
        user.setUserState(UserState.ACTIVE);
        user.setRegisteredDate(now());
        em.persist(user);

        AdminDetails admin = new AdminDetails();
        admin.setUser(user);
        admin.setAdminCode("ADM" + String.format("%04d", n));

        em.persist(admin);
        em.flush();
        return admin;
    }

    /** Builder base con los NOT NULL cubiertos; cada test ajusta status/assignedAdmin/dueDate/purchaseItem según el caso. */
    private Pqrs.PqrsBuilder basePqrs(User requester) {
        long n = SEQ.getAndIncrement();
        return Pqrs.builder()
                .type(PqrsType.RECLAMO)
                .requester(requester)
                .subject("Asunto de prueba " + n)
                .description("Descripción de prueba " + n)
                .dueDate(now().plusDays(5));
    }

    private Pqrs persist(Pqrs pqrs) {
        em.persist(pqrs);
        em.flush();
        return pqrs;
    }

    /** Grafo mínimo para poder vincular un Pqrs a un PurchaseItem real (Pqrs.purchaseItem es @ManyToOne). */
    private PurchaseItem persistPurchaseItem() {
        ConsumerDetails consumer = TestEntities.persistConsumer(em);
        CommercialDetails commercial = TestEntities.persistCommercial(em);

        ProductCategory category = new ProductCategory();
        category.setName("Categoria pqrs repo " + SEQ.getAndIncrement());
        em.persist(category);
        em.flush();

        Product product = new Product();
        product.setCommercial(commercial);
        product.setProductCategory(category);
        product.setName("Producto pqrs repo");
        product.setDescription("Descripción");
        product.setPriceCents(10000L);
        product.setMaxKeysPct(20);
        em.persist(product);
        em.flush();

        long n = SEQ.getAndIncrement();
        Purchase purchase = new Purchase();
        purchase.setReferenceId("REF-PQRS-" + n);
        purchase.setConsumer(consumer);
        purchase.setStatus(PurchaseStatus.COMPLETED);
        purchase.setTotalCents(10000L);
        purchase.setCashCents(10000L);
        purchase.setCommissionCents(1000L);
        purchase.setNetToCommercialsCents(9000L);
        em.persist(purchase);
        em.flush();

        PurchaseItem item = PurchaseItem.builder()
                .purchase(purchase)
                .product(product)
                .productNameSnapshot(product.getName())
                .commercialId(commercial.getId())
                .unitPriceCents(10000L)
                .subtotalCents(10000L)
                .maxKeysPctAtPurchase(20)
                .createdAt(now())
                .build();
        em.persist(item);
        em.flush();
        return item;
    }

    // ==================== findByRequesterId ====================

    @Nested
    @DisplayName("findByRequesterId")
    class FindByRequesterId {

        @Test
        @DisplayName("trae solo los PQRS del requester dado")
        void returnsOnlyPqrsOfGivenRequester() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);

            Pqrs mine = persist(basePqrs(requester.getUser()).build());
            persist(basePqrs(other.getUser()).build());

            Page<Pqrs> page = pqrsRepository.findByRequesterId(requester.getUser().getId(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Pqrs::getId).containsExactly(mine.getId());
        }
    }

    // ==================== findByAssignedAdminWithFilters ====================

    @Nested
    @DisplayName("findByAssignedAdminWithFilters")
    class FindByAssignedAdminWithFilters {

        @Test
        @DisplayName("sin filtros trae todos los PQRS asignados al admin")
        void noFiltersReturnsAllAssignedToAdmin() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            AdminDetails admin = persistAdmin(em);
            AdminDetails otherAdmin = persistAdmin(em);

            Pqrs assigned1 = persist(basePqrs(requester.getUser()).assignedAdmin(admin)
                    .status(PqrsStatus.RECIBIDA).build());
            Pqrs assigned2 = persist(basePqrs(requester.getUser()).assignedAdmin(admin)
                    .status(PqrsStatus.EN_REVISION).build());
            persist(basePqrs(requester.getUser()).assignedAdmin(otherAdmin).status(PqrsStatus.RECIBIDA).build());

            Page<Pqrs> page = pqrsRepository.findByAssignedAdminWithFilters(
                    admin.getUser().getId(), null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Pqrs::getId)
                    .containsExactlyInAnyOrder(assigned1.getId(), assigned2.getId());
        }

        @Test
        @DisplayName("filtra por status")
        void filtersByStatus() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            AdminDetails admin = persistAdmin(em);

            Pqrs recibida = persist(basePqrs(requester.getUser()).assignedAdmin(admin)
                    .status(PqrsStatus.RECIBIDA).build());
            persist(basePqrs(requester.getUser()).assignedAdmin(admin).status(PqrsStatus.EN_REVISION).build());

            Page<Pqrs> page = pqrsRepository.findByAssignedAdminWithFilters(
                    admin.getUser().getId(), PqrsStatus.RECIBIDA, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Pqrs::getId).containsExactly(recibida.getId());
        }

        @Test
        @DisplayName("filtra por type")
        void filtersByType() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            AdminDetails admin = persistAdmin(em);

            Pqrs queja = persist(basePqrs(requester.getUser()).assignedAdmin(admin).type(PqrsType.QUEJA)
                    .status(PqrsStatus.RECIBIDA).build());
            persist(basePqrs(requester.getUser()).assignedAdmin(admin).type(PqrsType.PETICION)
                    .status(PqrsStatus.RECIBIDA).build());

            Page<Pqrs> page = pqrsRepository.findByAssignedAdminWithFilters(
                    admin.getUser().getId(), null, PqrsType.QUEJA, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Pqrs::getId).containsExactly(queja.getId());
        }

        @Test
        @DisplayName("combina status y type")
        void combinesStatusAndType() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            AdminDetails admin = persistAdmin(em);

            Pqrs match = persist(basePqrs(requester.getUser()).assignedAdmin(admin).type(PqrsType.QUEJA)
                    .status(PqrsStatus.RECIBIDA).build());
            // Mismo type pero distinto status.
            persist(basePqrs(requester.getUser()).assignedAdmin(admin).type(PqrsType.QUEJA)
                    .status(PqrsStatus.EN_REVISION).build());
            // Mismo status pero distinto type.
            persist(basePqrs(requester.getUser()).assignedAdmin(admin).type(PqrsType.PETICION)
                    .status(PqrsStatus.RECIBIDA).build());

            Page<Pqrs> page = pqrsRepository.findByAssignedAdminWithFilters(
                    admin.getUser().getId(), PqrsStatus.RECIBIDA, PqrsType.QUEJA, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(Pqrs::getId).containsExactly(match.getId());
        }
    }

    // ==================== findByStatus ====================

    @Nested
    @DisplayName("findByStatus")
    class FindByStatus {

        @Test
        @DisplayName("derived query: retorna exactamente los PQRS con el status dado")
        void returnsExactMatchOfStatus() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);

            Pqrs resuelta = persist(basePqrs(requester.getUser()).status(PqrsStatus.RESUELTA).build());
            persist(basePqrs(requester.getUser()).status(PqrsStatus.RECIBIDA).build());
            persist(basePqrs(requester.getUser()).status(PqrsStatus.CERRADA).build());

            List<Pqrs> result = pqrsRepository.findByStatus(PqrsStatus.RESUELTA);

            assertThat(result).extracting(Pqrs::getId).containsExactly(resuelta.getId());
        }
    }

    // ==================== findByStatusInAndDueDateBefore ====================

    @Nested
    @DisplayName("findByStatusInAndDueDateBefore")
    class FindByStatusInAndDueDateBefore {

        @Test
        @DisplayName("trae los PQRS cuyo status está en la lista Y cuyo dueDate ya venció")
        void returnsMatchingStatusAndOverdueDueDate() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            ZonedDateTime threshold = now();

            Pqrs overdueReceived = persist(basePqrs(requester.getUser()).status(PqrsStatus.RECIBIDA)
                    .dueDate(threshold.minusDays(1)).build());
            Pqrs overdueInReview = persist(basePqrs(requester.getUser()).status(PqrsStatus.EN_REVISION)
                    .dueDate(threshold.minusHours(1)).build());

            List<Pqrs> result = pqrsRepository.findByStatusInAndDueDateBefore(
                    List.of(PqrsStatus.RECIBIDA, PqrsStatus.EN_REVISION), threshold);

            assertThat(result).extracting(Pqrs::getId)
                    .containsExactlyInAnyOrder(overdueReceived.getId(), overdueInReview.getId());
        }

        @Test
        @DisplayName("excluye PQRS que no cumplen el status, el plazo, o ninguno de los dos")
        void excludesPqrsFailingEitherCriterion() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            ZonedDateTime threshold = now();

            // Status correcto pero dueDate aún no vence.
            persist(basePqrs(requester.getUser()).status(PqrsStatus.RECIBIDA)
                    .dueDate(threshold.plusDays(1)).build());
            // dueDate vencido pero status no está en la lista.
            persist(basePqrs(requester.getUser()).status(PqrsStatus.RESUELTA)
                    .dueDate(threshold.minusDays(1)).build());
            // Ninguno de los dos criterios se cumple.
            persist(basePqrs(requester.getUser()).status(PqrsStatus.CERRADA)
                    .dueDate(threshold.plusDays(1)).build());

            List<Pqrs> result = pqrsRepository.findByStatusInAndDueDateBefore(
                    List.of(PqrsStatus.RECIBIDA, PqrsStatus.EN_REVISION), threshold);

            assertThat(result).isEmpty();
        }
    }

    // ==================== existsOpenByPurchaseItemId ====================

    @Nested
    @DisplayName("existsOpenByPurchaseItemId")
    class ExistsOpenByPurchaseItemId {

        @Test
        @DisplayName("true con un PQRS en estado no-terminal (ej. RECIBIDA) sobre ese purchaseItem")
        void trueWhenOpenPqrsExistsForItem() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);
            PurchaseItem item = persistPurchaseItem();
            persist(basePqrs(requester.getUser()).purchaseItem(item).status(PqrsStatus.RECIBIDA).build());

            assertThat(pqrsRepository.existsOpenByPurchaseItemId(item.getId())).isTrue();
        }

        @Test
        @DisplayName("false si el único PQRS de ese item está RESUELTA o CERRADA")
        void falseWhenOnlyPqrsIsResolvedOrClosed() {
            ConsumerDetails requester = TestEntities.persistConsumer(em);

            PurchaseItem resolvedItem = persistPurchaseItem();
            persist(basePqrs(requester.getUser()).purchaseItem(resolvedItem).status(PqrsStatus.RESUELTA).build());
            assertThat(pqrsRepository.existsOpenByPurchaseItemId(resolvedItem.getId())).isFalse();

            PurchaseItem closedItem = persistPurchaseItem();
            persist(basePqrs(requester.getUser()).purchaseItem(closedItem).status(PqrsStatus.CERRADA).build());
            assertThat(pqrsRepository.existsOpenByPurchaseItemId(closedItem.getId())).isFalse();
        }

        @Test
        @DisplayName("false si no hay ningún PQRS para ese purchaseItem")
        void falseWhenNoPqrsForItem() {
            PurchaseItem item = persistPurchaseItem();

            assertThat(pqrsRepository.existsOpenByPurchaseItemId(item.getId())).isFalse();
        }
    }
}
