package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

import com.verygana2.dtos.raffle.responses.RaffleSummaryResponseDTO;
import com.verygana2.dtos.raffle.responses.UserRaffleSummaryResponseDTO;
import com.verygana2.models.Department;
import com.verygana2.models.Municipality;
import com.verygana2.models.TargetAudience;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.PrizeType;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Prize;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleImageAsset;
import com.verygana2.models.raffles.RaffleRule;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.TicketEarningRule;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleRepository. Cubre las
 * consultas JPQL de admin/scheduler/usuario del dominio raffles.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        // NON_KEYWORDS=VALUE: Prize.value se mapea a una columna "value", que
        // H2 2.x reserva como palabra clave por defecto y rompe el CREATE TABLE
        // de raffle_prizes (aunque en MySQL real no es reservada).
        "spring.datasource.url=jdbc:h2:mem:raffle-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;NON_KEYWORDS=VALUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleRepository (integración H2)")
class RaffleRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleRepository raffleRepository;

    // ==================== HELPERS ====================

    /**
     * Persiste una rifa con el status deseado. onCreate() fuerza DRAFT al
     * persistir por primera vez, así que si se pide otro status se hace un
     * segundo flush actualizándolo, igual que RaffleTicketConcurrencyIntegrationTest.
     */
    private Raffle persistRaffle(String title, RaffleType type, RaffleStatus status,
            ZonedDateTime start, ZonedDateTime end, ZonedDateTime draw) {
        Raffle raffle = new Raffle();
        raffle.setTitle(title);
        raffle.setDescription("Descripción de " + title);
        raffle.setRaffleType(type);
        raffle.setStartDate(start);
        raffle.setEndDate(end);
        raffle.setDrawDate(draw);
        raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
        raffle.setCreatedBy(1L);
        em.persist(raffle);
        em.flush();

        if (status != null && status != RaffleStatus.DRAFT) {
            raffle.setRaffleStatus(status);
            em.flush();
        }
        return raffle;
    }

    private RaffleImageAsset persistImageAsset(Raffle raffle, String objectKey) {
        RaffleImageAsset asset = new RaffleImageAsset();
        asset.setObjectKey(objectKey);
        asset.setSizeBytes(1024L);
        asset.setRaffle(raffle);
        em.persist(asset);
        em.flush();
        return asset;
    }

    private Prize persistPrize(Raffle raffle, int position) {
        Prize prize = new Prize();
        prize.setRaffle(raffle);
        prize.setTitle("Premio " + position + " - " + raffle.getTitle());
        prize.setValue(BigDecimal.valueOf(100));
        prize.setPosition(position);
        prize.setQuantity(1);
        prize.setPrizeType(PrizeType.PHYSICAL);
        prize.setClaimCode("CLAIM-" + raffle.getId() + "-" + position);
        em.persist(prize);
        em.flush();
        return prize;
    }

    private TicketEarningRule persistEarningRule(String ruleName) {
        TicketEarningRule rule = new TicketEarningRule();
        rule.setRuleName(ruleName);
        rule.setRuleType(com.verygana2.models.enums.raffles.TicketEarningRuleType.PURCHASE);
        rule.setPriority(1);
        rule.setTicketsToAward(1);
        em.persist(rule);
        em.flush();
        return rule;
    }

    private RaffleRule persistRaffleRule(Raffle raffle, TicketEarningRule rule) {
        RaffleRule raffleRule = new RaffleRule();
        raffleRule.setRaffle(raffle);
        raffleRule.setTicketEarningRule(rule);
        em.persist(raffleRule);
        em.flush();
        return raffleRule;
    }

    private Municipality persistMunicipality(String code, String name, String deptCode, String deptName) {
        Department department = em.find(Department.class, deptCode);
        if (department == null) {
            department = new Department();
            department.setCode(deptCode);
            department.setName(deptName);
            em.persist(department);
        }
        Municipality municipality = em.find(Municipality.class, code);
        if (municipality == null) {
            municipality = new Municipality();
            municipality.setCode(code);
            municipality.setName(name);
            municipality.setDepartment(department);
            em.persist(municipality);
        }
        em.flush();
        return municipality;
    }

    private TargetAudience persistTargetAudience(Municipality... municipalities) {
        TargetAudience ta = TargetAudience.builder()
                .targetMunicipalities(new ArrayList<>(List.of(municipalities)))
                .build();
        em.persist(ta);
        em.flush();
        return ta;
    }

    private RaffleTicket persistTicket(Raffle raffle, ConsumerDetails owner, String ticketNumber, boolean winner) {
        RaffleTicket ticket = new RaffleTicket();
        ticket.setRaffle(raffle);
        ticket.setTicketOwner(owner);
        ticket.setTicketNumber(ticketNumber);
        ticket.setSource(RaffleTicketSource.PURCHASE);
        ticket.setSourceId(1L);
        em.persist(ticket);
        em.flush();
        if (winner) {
            ticket.setIsWinner(true);
            em.flush();
        }
        return ticket;
    }

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    // ==================== findByIdForUpdate ====================

    @Nested
    @DisplayName("findByIdForUpdate")
    class FindByIdForUpdate {

        @Test
        @DisplayName("recupera la rifa correcta bajo lock PESSIMISTIC_WRITE")
        void returnsRaffleUnderPessimisticLock() {
            Raffle raffle = persistRaffle("Rifa lock", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));

            Optional<Raffle> found = raffleRepository.findByIdForUpdate(raffle.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getId()).isEqualTo(raffle.getId());
            assertThat(found.get().getTitle()).isEqualTo("Rifa lock");
        }

        @Test
        @DisplayName("retorna Optional.empty si el id no existe")
        void returnsEmptyWhenNotFound() {
            Optional<Raffle> found = raffleRepository.findByIdForUpdate(999999L);
            assertThat(found).isEmpty();
        }
    }

    // ==================== findByFilters ====================

    @Nested
    @DisplayName("findByFilters")
    class FindByFilters {

        @Test
        @DisplayName("filtra por status y proyecta los campos esperados incluyendo el conteo de premios")
        void filtersByStatusAndProjectsFields() {
            Raffle active = persistRaffle("Rifa activa filtros", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(active, "img-active.png");
            persistPrize(active, 1);

            Raffle draft = persistRaffle("Rifa borrador filtros", RaffleType.STANDARD, RaffleStatus.DRAFT,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(draft, "img-draft.png");
            persistPrize(draft, 1);

            Page<RaffleSummaryResponseDTO> page = raffleRepository.findByFilters(
                    RaffleStatus.ACTIVE, null, null, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            RaffleSummaryResponseDTO dto = page.getContent().get(0);
            assertThat(dto.getId()).isEqualTo(active.getId());
            assertThat(dto.getTitle()).isEqualTo("Rifa activa filtros");
            assertThat(dto.getImageUrl()).isEqualTo("img-active.png");
            assertThat(dto.getRaffleType()).isEqualTo(RaffleType.STANDARD);
            assertThat(dto.getRaffleStatus()).isEqualTo(RaffleStatus.ACTIVE);
            assertThat(dto.getTotalTicketsIssued()).isEqualTo(0);
            assertThat(dto.getTotalParticipants()).isEqualTo(0);
            assertThat(dto.getPrizeCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("filtra por rango de fecha de sorteo")
        void filtersByDrawDateRange() {
            Raffle soon = persistRaffle("Rifa sorteo cercano", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(2), now().plusDays(3));
            persistImageAsset(soon, "img-soon.png");
            persistPrize(soon, 1);

            Raffle later = persistRaffle("Rifa sorteo lejano", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(30), now().plusDays(60));
            persistImageAsset(later, "img-later.png");
            persistPrize(later, 1);

            Page<RaffleSummaryResponseDTO> page = raffleRepository.findByFilters(
                    null, null, now(), now().plusDays(10), null, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(RaffleSummaryResponseDTO::getId)
                    .containsExactly(soon.getId());
        }

        @Test
        @DisplayName("filtra por texto de búsqueda en el título, sin distinguir mayúsculas")
        void filtersBySearchText() {
            Raffle carro = persistRaffle("Gran Rifa del Carro", RaffleType.PREMIUM, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(carro, "img-carro.png");
            persistPrize(carro, 1);

            Raffle moto = persistRaffle("Rifa de la Moto", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(moto, "img-moto.png");
            persistPrize(moto, 1);

            Page<RaffleSummaryResponseDTO> page = raffleRepository.findByFilters(
                    null, "carro", null, null, null, PageRequest.of(0, 10));

            assertThat(page.getContent())
                    .extracting(RaffleSummaryResponseDTO::getId)
                    .containsExactly(carro.getId());
        }
    }

    // ==================== countByRaffleStatus ====================

    @Nested
    @DisplayName("countByRaffleStatus")
    class CountByRaffleStatus {

        @Test
        @DisplayName("cuenta solo las rifas con el status pedido")
        void countsOnlyMatchingStatus() {
            persistRaffle("Rifa activa 1", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistRaffle("Rifa activa 2", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistRaffle("Rifa cerrada", RaffleType.STANDARD, RaffleStatus.CLOSED,
                    now().minusDays(10), now().minusDays(1), now().plusDays(1));

            assertThat(raffleRepository.countByRaffleStatus(RaffleStatus.ACTIVE)).isEqualTo(2);
            assertThat(raffleRepository.countByRaffleStatus(RaffleStatus.CLOSED)).isEqualTo(1);
            assertThat(raffleRepository.countByRaffleStatus(RaffleStatus.DRAWING)).isEqualTo(0);
        }
    }

    // ==================== findActiveRaffleByDrawDate ====================

    @Nested
    @DisplayName("findActiveRaffleByDrawDate")
    class FindActiveRaffleByDrawDate {

        @Test
        @DisplayName("trae rifas ACTIVE cuyo rango [startDate, endDate) contiene 'now', con reglas ya inicializadas")
        void returnsRaffleWithinRangeWithRulesFetched() {
            Raffle inRange = persistRaffle("Rifa en rango", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            TicketEarningRule rule = persistEarningRule("Regla activa rango");
            persistRaffleRule(inRange, rule);

            Raffle outOfRange = persistRaffle("Rifa fuera de rango", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().plusDays(1), now().plusDays(5), now().plusDays(6));

            // inRange fue construido a mano (new Raffle()) y persistido, así que su
            // campo raffleRules quedó en null (nunca se inicializó vía una query).
            // Sin limpiar el contexto de persistencia, el identity map devolvería
            // esa MISMA instancia para la consulta de abajo y el LEFT JOIN FETCH no
            // repoblaría ese campo ya "cargado" (aunque esté en null).
            em.clear();

            List<Raffle> result = raffleRepository.findActiveRaffleByDrawDate(now());

            assertThat(result).extracting(Raffle::getId).containsExactly(inRange.getId());
            assertThat(result.get(0).getRaffleRules()).hasSize(1);
            assertThat(result.get(0).getRaffleRules().get(0).getTicketEarningRule().getRuleName())
                    .isEqualTo("Regla activa rango");
            assertThat(outOfRange).isNotNull();
        }
    }

    // ==================== findRafflesToActivate ====================

    @Nested
    @DisplayName("findRafflesToActivate")
    class FindRafflesToActivate {

        @Test
        @DisplayName("trae rifas DRAFT dentro de rango con premios y reglas ya configurados")
        void returnsDraftRaffleWithPrizesAndRules() {
            Raffle ready = persistRaffle("Rifa lista para activar", RaffleType.STANDARD, RaffleStatus.DRAFT,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistPrize(ready, 1);
            TicketEarningRule rule = persistEarningRule("Regla para activar");
            persistRaffleRule(ready, rule);

            List<Raffle> result = raffleRepository.findRafflesToActivate(now());

            assertThat(result).extracting(Raffle::getId).containsExactly(ready.getId());
        }

        @Test
        @DisplayName("NO trae rifas DRAFT sin premios ni reglas configuradas")
        void excludesDraftRaffleWithoutPrizesOrRules() {
            persistRaffle("Rifa sin premios ni reglas", RaffleType.STANDARD, RaffleStatus.DRAFT,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));

            List<Raffle> result = raffleRepository.findRafflesToActivate(now());

            assertThat(result).isEmpty();
        }
    }

    // ==================== findRafflesToClose ====================

    @Nested
    @DisplayName("findRafflesToClose")
    class FindRafflesToClose {

        @Test
        @DisplayName("trae rifas ACTIVE cuya endDate ya pasó")
        void returnsActiveRaffleWithPastEndDate() {
            Raffle toClose = persistRaffle("Rifa por cerrar", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(10), now().minusHours(1), now().plusDays(1));
            Raffle stillOpen = persistRaffle("Rifa aún abierta", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));

            List<Raffle> result = raffleRepository.findRafflesToClose(now());

            assertThat(result).extracting(Raffle::getId).containsExactly(toClose.getId());
            assertThat(stillOpen).isNotNull();
        }
    }

    // ==================== findRafflesToSetLive ====================

    @Nested
    @DisplayName("findRafflesToSetLive")
    class FindRafflesToSetLive {

        @Test
        @DisplayName("trae rifas CLOSED cuya drawDate cae dentro del umbral 'live'")
        void returnsClosedRaffleWithinLiveThreshold() {
            ZonedDateTime liveThreshold = now().plusHours(1);
            Raffle aboutToDraw = persistRaffle("Rifa por sortear", RaffleType.STANDARD, RaffleStatus.CLOSED,
                    now().minusDays(10), now().minusHours(1), now().plusMinutes(30));
            Raffle farAway = persistRaffle("Rifa lejos del sorteo", RaffleType.STANDARD, RaffleStatus.CLOSED,
                    now().minusDays(10), now().minusHours(1), now().plusDays(5));

            List<Raffle> result = raffleRepository.findRafflesToSetLive(now(), liveThreshold);

            assertThat(result).extracting(Raffle::getId).containsExactly(aboutToDraw.getId());
            assertThat(farAway).isNotNull();
        }
    }

    // ==================== findMissedDrawRaffles ====================

    @Nested
    @DisplayName("findMissedDrawRaffles")
    class FindMissedDrawRaffles {

        @Test
        @DisplayName("trae rifas CLOSED cuya drawDate ya pasó sin sortearse")
        void returnsClosedRaffleWithPastDrawDate() {
            Raffle missed = persistRaffle("Rifa con sorteo perdido", RaffleType.STANDARD, RaffleStatus.CLOSED,
                    now().minusDays(10), now().minusDays(2), now().minusHours(1));
            Raffle pending = persistRaffle("Rifa con sorteo pendiente", RaffleType.STANDARD, RaffleStatus.CLOSED,
                    now().minusDays(10), now().minusDays(2), now().plusHours(1));

            List<Raffle> result = raffleRepository.findMissedDrawRaffles(now());

            assertThat(result).extracting(Raffle::getId).containsExactly(missed.getId());
            assertThat(pending).isNotNull();
        }
    }

    // ==================== findLiveRafflesWithDrawDateBefore ====================

    @Nested
    @DisplayName("findLiveRafflesWithDrawDateBefore")
    class FindLiveRafflesWithDrawDateBefore {

        @Test
        @DisplayName("trae rifas con status literal LIVE y drawDate <= horizon")
        void returnsLiveRaffleBeforeHorizon() {
            ZonedDateTime horizon = now().plusMinutes(10);
            Raffle live = persistRaffle("Rifa en vivo", RaffleType.STANDARD, RaffleStatus.LIVE,
                    now().minusDays(10), now().minusDays(2), now().plusMinutes(5));
            Raffle liveButFar = persistRaffle("Rifa en vivo lejana", RaffleType.STANDARD, RaffleStatus.LIVE,
                    now().minusDays(10), now().minusDays(2), now().plusDays(2));

            List<Raffle> result = raffleRepository.findLiveRafflesWithDrawDateBefore(horizon);

            assertThat(result).extracting(Raffle::getId).containsExactly(live.getId());
            assertThat(liveButFar).isNotNull();
        }
    }

    // ==================== findLiveRaffles / findActiveRaffles ====================

    @Nested
    @DisplayName("findLiveRaffles y findActiveRaffles")
    class FindLiveAndActiveRaffles {

        @Test
        @DisplayName("findLiveRaffles: sin filtro de municipio trae todas las rifas LIVE con premios")
        void findLiveRafflesWithoutMunicipalityFilterReturnsAll() {
            Raffle live = persistRaffle("Rifa en vivo sin filtro", RaffleType.STANDARD, RaffleStatus.LIVE,
                    now().minusDays(2), now().minusDays(1), now().plusHours(1));
            persistImageAsset(live, "img-live.png");
            persistPrize(live, 1);

            List<RaffleSummaryResponseDTO> result = raffleRepository.findLiveRaffles(null);

            assertThat(result).extracting(RaffleSummaryResponseDTO::getId).contains(live.getId());
        }

        @Test
        @DisplayName("findLiveRaffles: con filtro de municipio excluye rifas dirigidas a otro municipio")
        void findLiveRafflesFiltersByMunicipality() {
            Municipality armenia = persistMunicipality("63001", "Armenia", "63", "Quindío");
            Municipality medellin = persistMunicipality("05001", "Medellín", "05", "Antioquia");

            Raffle targetedToMedellin = persistRaffle("Rifa dirigida a Medellín", RaffleType.STANDARD,
                    RaffleStatus.LIVE, now().minusDays(2), now().minusDays(1), now().plusHours(1));
            persistImageAsset(targetedToMedellin, "img-medellin.png");
            persistPrize(targetedToMedellin, 1);
            targetedToMedellin.setTargetAudience(persistTargetAudience(medellin));
            em.persist(targetedToMedellin);
            em.flush();

            Raffle noTarget = persistRaffle("Rifa sin audiencia dirigida", RaffleType.STANDARD, RaffleStatus.LIVE,
                    now().minusDays(2), now().minusDays(1), now().plusHours(1));
            persistImageAsset(noTarget, "img-notarget.png");
            persistPrize(noTarget, 1);

            List<RaffleSummaryResponseDTO> resultForArmenia = raffleRepository.findLiveRaffles(armenia);

            assertThat(resultForArmenia)
                    .extracting(RaffleSummaryResponseDTO::getId)
                    .contains(noTarget.getId())
                    .doesNotContain(targetedToMedellin.getId());
        }

        @Test
        @DisplayName("findActiveRaffles: filtra por tipo y por municipio, pagina el resultado")
        void findActiveRafflesFiltersByTypeAndMunicipality() {
            Municipality armenia = persistMunicipality("63001", "Armenia", "63", "Quindío");
            Municipality medellin = persistMunicipality("05001", "Medellín", "05", "Antioquia");

            Raffle premiumForMedellin = persistRaffle("Rifa premium Medellín", RaffleType.PREMIUM,
                    RaffleStatus.ACTIVE, now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(premiumForMedellin, "img-premium-medellin.png");
            persistPrize(premiumForMedellin, 1);
            premiumForMedellin.setTargetAudience(persistTargetAudience(medellin));
            em.persist(premiumForMedellin);
            em.flush();

            Raffle standardOpen = persistRaffle("Rifa estándar abierta", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(standardOpen, "img-standard.png");
            persistPrize(standardOpen, 1);

            Page<RaffleSummaryResponseDTO> onlyStandard = raffleRepository.findActiveRaffles(
                    RaffleType.STANDARD, null, PageRequest.of(0, 10));
            assertThat(onlyStandard.getContent())
                    .extracting(RaffleSummaryResponseDTO::getId)
                    .containsExactly(standardOpen.getId());

            Page<RaffleSummaryResponseDTO> allTypesForArmenia = raffleRepository.findActiveRaffles(
                    null, armenia, PageRequest.of(0, 10));
            assertThat(allTypesForArmenia.getContent())
                    .extracting(RaffleSummaryResponseDTO::getId)
                    .contains(standardOpen.getId())
                    .doesNotContain(premiumForMedellin.getId());

            Page<RaffleSummaryResponseDTO> allTypesNoFilter = raffleRepository.findActiveRaffles(
                    null, null, PageRequest.of(0, 10));
            assertThat(allTypesNoFilter.getContent())
                    .extracting(RaffleSummaryResponseDTO::getId)
                    .contains(standardOpen.getId(), premiumForMedellin.getId());
        }
    }

    // ==================== findMyRafflesByStatus / countMyRafflesByStatus ====================

    @Nested
    @DisplayName("findMyRafflesByStatus y countMyRafflesByStatus")
    class FindMyRafflesByStatus {

        @Test
        @DisplayName("cuenta tickets distintos del usuario y marca isWinner cuando algún ticket ganó")
        void countsDistinctTicketsAndFlagsWinner() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);

            Raffle raffle = persistRaffle("Rifa con tickets del usuario", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            // r.imageAsset.objectKey en la proyección es una navegación implícita
            // (INNER JOIN): sin un RaffleImageAsset asociado, la rifa quedaría
            // excluida del resultado aunque tenga tickets.
            persistImageAsset(raffle, "img-my-raffles.png");
            persistTicket(raffle, consumer, "T-1", false);
            persistTicket(raffle, consumer, "T-2", true);

            Page<UserRaffleSummaryResponseDTO> page = raffleRepository.findMyRafflesByStatus(
                    consumer.getId(), RaffleStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            UserRaffleSummaryResponseDTO dto = page.getContent().get(0);
            assertThat(dto.getId()).isEqualTo(raffle.getId());
            assertThat(dto.getUserTicketCount()).isEqualTo(2L);
            assertThat(dto.isWinner()).isTrue();

            long count = raffleRepository.countMyRafflesByStatus(consumer.getId(), RaffleStatus.ACTIVE);
            assertThat(count).isEqualTo(1);
        }

        @Test
        @DisplayName("isWinner es false cuando ningún ticket del usuario ganó")
        void isWinnerFalseWhenNoWinningTicket() {
            ConsumerDetails consumer = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa sin ganadores del usuario", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistImageAsset(raffle, "img-no-winners.png");
            persistTicket(raffle, consumer, "T-1", false);

            Page<UserRaffleSummaryResponseDTO> page = raffleRepository.findMyRafflesByStatus(
                    consumer.getId(), RaffleStatus.ACTIVE, PageRequest.of(0, 10));

            assertThat(page.getContent()).hasSize(1);
            assertThat(page.getContent().get(0).isWinner()).isFalse();
        }
    }

    // ==================== findByIdWithPrizes ====================

    @Nested
    @DisplayName("findByIdWithPrizes")
    class FindByIdWithPrizes {

        @Test
        @DisplayName("trae la rifa con sus premios ya inicializados, sin LazyInitializationException")
        void fetchesPrizesEagerly() {
            Raffle raffle = persistRaffle("Rifa con premios fetch", RaffleType.STANDARD, RaffleStatus.ACTIVE,
                    now().minusDays(1), now().plusDays(5), now().plusDays(6));
            persistPrize(raffle, 1);
            persistPrize(raffle, 2);

            em.clear();

            Optional<Raffle> found = raffleRepository.findByIdWithPrizes(raffle.getId());

            assertThat(found).isPresent();
            assertThat(found.get().getPrizes()).hasSize(2);
            assertThat(found.get().getPrizes()).extracting(Prize::getPosition).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        @DisplayName("retorna Optional.empty si la rifa no existe")
        void returnsEmptyWhenNotFound() {
            Optional<Raffle> found = raffleRepository.findByIdWithPrizes(999999L);
            assertThat(found).isEmpty();
        }
    }
}
