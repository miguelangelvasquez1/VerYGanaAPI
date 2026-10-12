package com.verygana2.repositories.finance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
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

import com.verygana2.models.enums.finance.KeyTransactionType;
import com.verygana2.models.finance.KeyTransaction;
import com.verygana2.models.finance.KeyWallet;
import com.verygana2.models.pets.CatalogIntegrationRequest;
import com.verygana2.models.pets.PetCatalogItem;
import com.verygana2.models.userDetails.CommercialDetails;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para KeyTransactionRepository — el
 * repositorio más grande del dominio finance. Cubre el historial paginado
 * filtrado, las 3 sumas agregadas (con COALESCE: SUM sobre vacío = 0, nunca
 * null), la búsqueda por clave de idempotencia y las 3 queries nativas de
 * ventas de mascotas.
 *
 * Las 3 queries nativas usan sintaxis estándar (JOIN/LEFT JOIN, HAVING,
 * alias no reservados) compatible con H2 en MODE=MySQL, así que se cubren
 * todas. (findPetDailySalesByCommercial usaba "AS day" —reservada en H2— y
 * ahora es "AS saleDay".)
 */
@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:key-transaction-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("KeyTransactionRepository (integración H2)")
class KeyTransactionRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private KeyTransactionRepository keyTransactionRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private KeyWallet persistKeyWallet(ConsumerDetails consumer) {
        KeyWallet wallet = KeyWallet.createFor(consumer);
        em.persist(wallet);
        em.flush();
        return wallet;
    }

    private KeyTransaction persistTransaction(KeyWallet wallet, KeyTransactionType type,
            Long purchaseDeltaCents, Long connectivityDeltaCents, ZonedDateTime expiredAt) {
        KeyTransaction kt = KeyTransaction.builder()
                .keyWallet(wallet)
                .type(type)
                .purchaseKeysDeltaCents(purchaseDeltaCents)
                .connectivityKeysDeltaCents(connectivityDeltaCents)
                .reason("Movimiento de prueba")
                .referenceId(UUID.randomUUID())
                .expiredAt(expiredAt)
                .idempotencyKey("TEST:" + UUID.randomUUID())
                .build();
        em.persist(kt);
        em.flush();
        return kt;
    }

    private KeyTransaction persistPetGameTransaction(KeyWallet wallet, Long petCatalogItemId, long spentCents) {
        KeyTransaction kt = KeyTransaction.builder()
                .keyWallet(wallet)
                .type(KeyTransactionType.DEBIT_PET_GAME)
                .purchaseKeysDeltaCents(-spentCents)
                .petCatalogItemId(petCatalogItemId)
                .reason("Mascota virtual: prueba")
                .referenceId(UUID.randomUUID())
                .idempotencyKey("TEST:" + UUID.randomUUID())
                .build();
        em.persist(kt);
        em.flush();
        return kt;
    }

    private PetCatalogItem persistPetCatalogItem(String name, Integer externalId, Integer price) {
        PetCatalogItem item = new PetCatalogItem();
        item.setName(name);
        item.setExternalId(externalId);
        item.setPrice(price);
        item.setActive(true);
        em.persist(item);
        em.flush();
        return item;
    }

    private CatalogIntegrationRequest persistCatalogIntegrationRequest(CommercialDetails commercial,
            Long resultCatalogItemId) {
        CatalogIntegrationRequest request = new CatalogIntegrationRequest();
        request.setCommercial(commercial);
        request.setProductName("Solicitud de integración test");
        request.setDescription("Descripción de la solicitud");
        request.setDesiredEffects("Sube energía");
        request.setResultCatalogItemId(resultCatalogItemId);
        em.persist(request);
        em.flush();
        return request;
    }

    // ==================== findByConsumerId ====================

    @Nested
    @DisplayName("findByConsumerId")
    class FindByConsumerId {

        @Test
        @DisplayName("sin filtros opcionales trae todas las transacciones del consumer, ordenadas por createdAt DESC")
        void noFiltersReturnsAllOrderedDesc() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            KeyTransaction t1 = persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 100L, null, null);
            KeyTransaction t2 = persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 200L, null, null);
            // KeyTransaction.onCreate() fuerza createdAt = now() en el @PrePersist: dos
            // inserts consecutivos pueden empatar en el mismo instante y volver el orden
            // no determinístico. Se fuerza una separación clara vía UPDATE JPQL para que
            // el test del ORDER BY sea estable.
            em.createQuery("UPDATE KeyTransaction kt SET kt.createdAt = :d WHERE kt.id = :id")
                    .setParameter("d", now().minusMinutes(1)).setParameter("id", t1.getId()).executeUpdate();
            em.createQuery("UPDATE KeyTransaction kt SET kt.createdAt = :d WHERE kt.id = :id")
                    .setParameter("d", now()).setParameter("id", t2.getId()).executeUpdate();
            em.clear();

            Page<KeyTransaction> page = keyTransactionRepository.findByConsumerId(
                    consumer.getId(), null, null, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).extracting(KeyTransaction::getId)
                    .containsExactly(t2.getId(), t1.getId());
        }

        @Test
        @DisplayName("filtra por type cuando se especifica")
        void filtersByType() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 100L, null, null);
            KeyTransaction debit = persistTransaction(wallet, KeyTransactionType.DEBIT_COPAYMENT, -50L, null, null);

            Page<KeyTransaction> page = keyTransactionRepository.findByConsumerId(
                    consumer.getId(), null, null, KeyTransactionType.DEBIT_COPAYMENT, PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(KeyTransaction::getId).containsExactly(debit.getId());
        }

        @Test
        @DisplayName("filtra por rango de fechas cuando initialDate/endDate se especifican")
        void filtersByDateRange() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 100L, null, null);

            Page<KeyTransaction> future = keyTransactionRepository.findByConsumerId(
                    consumer.getId(), now().plusDays(1), null, null, PageRequest.of(0, 10));
            Page<KeyTransaction> past = keyTransactionRepository.findByConsumerId(
                    consumer.getId(), null, now().minusDays(1), null, PageRequest.of(0, 10));
            Page<KeyTransaction> withinRange = keyTransactionRepository.findByConsumerId(
                    consumer.getId(), now().minusDays(1), now().plusDays(1), null, PageRequest.of(0, 10));

            assertThat(future.getContent()).isEmpty();
            assertThat(past.getContent()).isEmpty();
            assertThat(withinRange.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("no trae transacciones de otro consumer")
        void excludesOtherConsumerTransactions() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            persistKeyWallet(other);
            persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 100L, null, null);

            Page<KeyTransaction> page = keyTransactionRepository.findByConsumerId(
                    other.getId(), null, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).isEmpty();
        }
    }

    // ==================== sumTotalEarnedKeysCents / sumTotalUsedKeysCents / sumTotalExpiredKeysCents ====================

    @Nested
    @DisplayName("sumas agregadas de llaves")
    class SumAggregates {

        @Test
        @DisplayName("sumTotalEarnedKeysCents retorna 0 (COALESCE sobre SUM vacío) cuando no hay créditos")
        void sumEarnedReturnsZeroWhenNoRows() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            persistKeyWallet(consumer);

            assertThat(keyTransactionRepository.sumTotalEarnedKeysCents(consumer.getId())).isZero();
        }

        @Test
        @DisplayName("sumTotalEarnedKeysCents suma purchase+connectivity de los tipos CREDIT_*")
        void sumEarnedSumsCreditTypes() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 1000L, 200L, null);
            persistTransaction(wallet, KeyTransactionType.CREDIT_REFERRAL_BONUS, 500L, null, null);
            persistTransaction(wallet, KeyTransactionType.CREDIT_ADMIN_ADJUSTMENT, null, 100L, null);
            // No debe contarse: no es un tipo de crédito.
            persistTransaction(wallet, KeyTransactionType.DEBIT_COPAYMENT, -300L, null, null);

            assertThat(keyTransactionRepository.sumTotalEarnedKeysCents(consumer.getId())).isEqualTo(1800L);
        }

        @Test
        @DisplayName("sumTotalUsedKeysCents retorna 0 cuando no hay débitos")
        void sumUsedReturnsZeroWhenNoRows() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 1000L, null, null);

            assertThat(keyTransactionRepository.sumTotalUsedKeysCents(consumer.getId())).isZero();
        }

        @Test
        @DisplayName("sumTotalUsedKeysCents suma purchase+connectivity de los tipos DEBIT_*")
        void sumUsedSumsDebitTypes() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            persistTransaction(wallet, KeyTransactionType.DEBIT_COPAYMENT, -300L, null, null);
            persistTransaction(wallet, KeyTransactionType.DEBIT_CONNECTIVITY_RECHARGE, null, -150L, null);
            persistTransaction(wallet, KeyTransactionType.DEBIT_ADMIN_ADJUSTMENT, -50L, null, null);
            // No debe contarse: no es un tipo de débito de esta suma (es DEBIT_PET_GAME).
            persistTransaction(wallet, KeyTransactionType.DEBIT_PET_GAME, -1000L, null, null);

            assertThat(keyTransactionRepository.sumTotalUsedKeysCents(consumer.getId())).isEqualTo(-500L);
        }

        @Test
        @DisplayName("sumTotalUsedKeysCents cuenta la confirmación de un copago, cuyo monto va en el delta bloqueado")
        void sumUsedCountsBlockedDeltaOfConfirmedCopayment() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            // La reserva baja el disponible pero no es un uso todavía: no debe contarse.
            persistTransaction(wallet, KeyTransactionType.RESERVE_COPAYMENT_PENDING, -300L, null, null);
            KeyTransaction confirm = KeyTransaction.builder()
                    .keyWallet(wallet)
                    .type(KeyTransactionType.DEBIT_COPAYMENT)
                    .blockedPurchaseDeltaCents(-300L)
                    .reason("Pago con llaves: prueba")
                    .referenceId(UUID.randomUUID())
                    .idempotencyKey("TEST:" + UUID.randomUUID())
                    .build();
            em.persist(confirm);
            em.flush();

            assertThat(keyTransactionRepository.sumTotalUsedKeysCents(consumer.getId())).isEqualTo(-300L);
        }

        @Test
        @DisplayName("findByIdempotencyKey encuentra el asiento por su clave y nada con una clave desconocida")
        void findsByIdempotencyKey() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);
            KeyTransaction kt = persistTransaction(wallet, KeyTransactionType.CREDIT_INTERACTION, 100L, null, null);

            assertThat(keyTransactionRepository.findByIdempotencyKey(kt.getIdempotencyKey()))
                    .get().extracting(KeyTransaction::getId).isEqualTo(kt.getId());
            assertThat(keyTransactionRepository.findByIdempotencyKey("NO-EXISTE")).isEmpty();
        }

        @Test
        @DisplayName("sumTotalExpiredKeysCents retorna 0 cuando no hay expiraciones, y suma cuando sí hay")
        void sumExpiredZeroThenSum() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(consumer);

            assertThat(keyTransactionRepository.sumTotalExpiredKeysCents(consumer.getId())).isZero();

            persistTransaction(wallet, KeyTransactionType.EXPIRED, -700L, -100L, null);

            assertThat(keyTransactionRepository.sumTotalExpiredKeysCents(consumer.getId())).isEqualTo(-800L);
        }
    }

    // ==================== findPetProductSalesByCommercial (query nativa) ====================

    @Nested
    @DisplayName("findPetProductSalesByCommercial (nativa)")
    class FindPetProductSalesByCommercial {

        @Test
        @DisplayName("un producto sin ventas en el período aparece igual, con contadores en cero (fecha en el ON, no en el WHERE)")
        void includesProductWithoutSalesInPeriodWithZeroCounts() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PetCatalogItem item = persistPetCatalogItem("Comida sin ventas", 900, 50);
            persistCatalogIntegrationRequest(commercial, item.getId());

            List<KeyTransactionRepository.PetProductSalesRow> rows = keyTransactionRepository
                    .findPetProductSalesByCommercial(commercial.getId(), now().minusMinutes(5), now().plusMinutes(5));

            assertThat(rows).hasSize(1);
            KeyTransactionRepository.PetProductSalesRow row = rows.get(0);
            assertThat(row.getCatalogItemId()).isEqualTo(item.getId());
            assertThat(row.getUnitsSold()).isZero();
            assertThat(row.getRevenueCents()).isZero();
            assertThat(row.getUniqueBuyers()).isZero();
            assertThat(row.getFirstSale()).isNull();
            assertThat(row.getLastSale()).isNull();
        }

        @Test
        @DisplayName("cuenta ventas reales, compradores únicos, y no mezcla productos de otro comercial")
        void countsRealSalesAndUniqueBuyersScopedToCommercial() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            CommercialDetails other = TestEntities.persistCommercial(em);
            PetCatalogItem item = persistPetCatalogItem("Comida con ventas", 901, 50);
            persistCatalogIntegrationRequest(commercial, item.getId());
            PetCatalogItem otherItem = persistPetCatalogItem("Comida de otro comercial", 902, 30);
            persistCatalogIntegrationRequest(other, otherItem.getId());

            ConsumerDetails buyer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(buyer);
            persistPetGameTransaction(wallet, item.getId(), 500L);
            persistPetGameTransaction(wallet, item.getId(), 500L);
            persistPetGameTransaction(wallet, otherItem.getId(), 300L);

            List<KeyTransactionRepository.PetProductSalesRow> rows = keyTransactionRepository
                    .findPetProductSalesByCommercial(commercial.getId(), now().minusMinutes(5), now().plusMinutes(5));

            assertThat(rows).hasSize(1);
            KeyTransactionRepository.PetProductSalesRow row = rows.get(0);
            assertThat(row.getCatalogItemId()).isEqualTo(item.getId());
            assertThat(row.getUnitsSold()).isEqualTo(2L);
            assertThat(row.getRevenueCents()).isEqualTo(1000L);
            assertThat(row.getUniqueBuyers()).isEqualTo(1L);
        }

        @Test
        @DisplayName("excluye ventas fuera del rango [from, to) sin hacer desaparecer el producto")
        void excludesSalesOutsideRangeWithoutDroppingProduct() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PetCatalogItem item = persistPetCatalogItem("Comida fuera de rango", 903, 50);
            persistCatalogIntegrationRequest(commercial, item.getId());

            ConsumerDetails buyer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(buyer);
            KeyTransaction sale = persistPetGameTransaction(wallet, item.getId(), 500L);
            // Se saca la venta del rango vía UPDATE JPQL porque KeyTransaction.onCreate()
            // fuerza createdAt = now() en el @PrePersist, así que no se puede setear
            // directamente al construir la entidad.
            em.createQuery("UPDATE KeyTransaction kt SET kt.createdAt = :d WHERE kt.id = :id")
                    .setParameter("d", now().minusDays(10))
                    .setParameter("id", sale.getId())
                    .executeUpdate();
            em.clear();

            List<KeyTransactionRepository.PetProductSalesRow> rows = keyTransactionRepository
                    .findPetProductSalesByCommercial(commercial.getId(), now().minusMinutes(5), now().plusMinutes(5));

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0).getUnitsSold()).isZero();
        }
    }

    // ==================== findPetDailySalesByCommercial (query nativa) ====================

    @Nested
    @DisplayName("findPetDailySalesByCommercial (nativa)")
    class FindPetDailySalesByCommercial {

        @Test
        @DisplayName("agrupa por día (DATE()) sumando unidades e ingresos del comercial en el rango")
        void groupsByDaySummingUnitsAndRevenue() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PetCatalogItem item = persistPetCatalogItem("Comida diaria", 910, 50);
            persistCatalogIntegrationRequest(commercial, item.getId());

            ConsumerDetails buyer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(buyer);
            persistPetGameTransaction(wallet, item.getId(), 500L);
            persistPetGameTransaction(wallet, item.getId(), 300L);

            List<KeyTransactionRepository.PetDailySalesRow> rows = keyTransactionRepository
                    .findPetDailySalesByCommercial(commercial.getId(), now().minusMinutes(5), now().plusMinutes(5));

            assertThat(rows).hasSize(1);
            KeyTransactionRepository.PetDailySalesRow row = rows.get(0);
            assertThat(row.getUnitsSold()).isEqualTo(2L);
            assertThat(row.getRevenueCents()).isEqualTo(800L);
        }

        @Test
        @DisplayName("no devuelve días sin ventas del comercial")
        void returnsNothingWhenNoSales() {
            CommercialDetails commercial = TestEntities.persistCommercial(em);
            PetCatalogItem item = persistPetCatalogItem("Comida sin historial", 911, 50);
            persistCatalogIntegrationRequest(commercial, item.getId());

            List<KeyTransactionRepository.PetDailySalesRow> rows = keyTransactionRepository
                    .findPetDailySalesByCommercial(commercial.getId(), now().minusMinutes(5), now().plusMinutes(5));

            assertThat(rows).isEmpty();
        }
    }

    // ==================== countRepeatBuyers (query nativa) ====================

    @Nested
    @DisplayName("countRepeatBuyers (nativa)")
    class CountRepeatBuyers {

        @Test
        @DisplayName("cuenta solo los consumers que compraron el mismo ítem más de una vez (HAVING COUNT(*) > 1)")
        void countsOnlyBuyersWithMoreThanOnePurchase() {
            PetCatalogItem item = persistPetCatalogItem("Comida repetidores", 920, 50);

            ConsumerDetails repeatBuyer = TestEntities.persistConsumer(em);
            KeyWallet repeatWallet = persistKeyWallet(repeatBuyer);
            persistPetGameTransaction(repeatWallet, item.getId(), 100L);
            persistPetGameTransaction(repeatWallet, item.getId(), 100L);

            ConsumerDetails singleBuyer = TestEntities.persistConsumer(em);
            KeyWallet singleWallet = persistKeyWallet(singleBuyer);
            persistPetGameTransaction(singleWallet, item.getId(), 100L);

            assertThat(keyTransactionRepository.countRepeatBuyers(item.getId())).isEqualTo(1L);
        }

        @Test
        @DisplayName("retorna 0 cuando ningún comprador repitió")
        void returnsZeroWhenNoRepeatBuyers() {
            PetCatalogItem item = persistPetCatalogItem("Comida sin repetidores", 921, 50);
            ConsumerDetails buyer = TestEntities.persistConsumer(em);
            KeyWallet wallet = persistKeyWallet(buyer);
            persistPetGameTransaction(wallet, item.getId(), 100L);

            assertThat(keyTransactionRepository.countRepeatBuyers(item.getId())).isZero();
        }
    }
}
