package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
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

import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleStatus;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleTicketStatus;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para RaffleTicketRepository. Cubre
 * todos los métodos del repositorio, incluyendo el UPDATE masivo de
 * expireTicketsByRaffle.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:raffle-ticket-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("RaffleTicketRepository (integración H2)")
class RaffleTicketRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private RaffleTicketRepository raffleTicketRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private Raffle persistRaffle(String title, RaffleStatus status, ZonedDateTime drawDate) {
        Raffle raffle = new Raffle();
        raffle.setTitle(title);
        raffle.setDescription("desc " + title);
        raffle.setRaffleType(RaffleType.STANDARD);
        raffle.setStartDate(now().minusDays(1));
        raffle.setEndDate(now().plusDays(5));
        raffle.setDrawDate(drawDate);
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

    private RaffleTicket persistTicket(Raffle raffle, ConsumerDetails owner, String ticketNumber,
            RaffleTicketSource source, long sourceId) {
        RaffleTicket ticket = new RaffleTicket();
        ticket.setRaffle(raffle);
        ticket.setTicketOwner(owner);
        ticket.setTicketNumber(ticketNumber);
        ticket.setSource(source);
        ticket.setSourceId(sourceId);
        em.persist(ticket);
        em.flush();
        return ticket;
    }

    private RaffleTicket setStatus(RaffleTicket ticket, RaffleTicketStatus status) {
        ticket.setStatus(status);
        em.flush();
        return ticket;
    }

    private RaffleTicket setWinner(RaffleTicket ticket, boolean winner) {
        ticket.setIsWinner(winner);
        em.flush();
        return ticket;
    }

    // ==================== findByTicketNumberAndRaffleId / existsByTicketNumberAndRaffleId ====================

    @Nested
    @DisplayName("findByTicketNumberAndRaffleId y existsByTicketNumberAndRaffleId")
    class FindAndExistsByTicketNumberAndRaffleId {

        @Test
        @DisplayName("encuentra el ticket exacto por número y rifa, y no lo encuentra bajo otra rifa")
        void findsExactTicketOnlyUnderCorrectRaffle() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffleA = persistRaffle("Rifa A", RaffleStatus.ACTIVE, now().plusDays(10));
            Raffle raffleB = persistRaffle("Rifa B", RaffleStatus.ACTIVE, now().plusDays(20));
            persistTicket(raffleA, owner, "T-100", RaffleTicketSource.PURCHASE, 1L);

            Optional<RaffleTicket> found = raffleTicketRepository.findByTicketNumberAndRaffleId("T-100", raffleA.getId());
            Optional<RaffleTicket> notFound = raffleTicketRepository.findByTicketNumberAndRaffleId("T-100", raffleB.getId());

            assertThat(found).isPresent();
            assertThat(notFound).isEmpty();

            assertThat(raffleTicketRepository.existsByTicketNumberAndRaffleId("T-100", raffleA.getId())).isTrue();
            assertThat(raffleTicketRepository.existsByTicketNumberAndRaffleId("T-100", raffleB.getId())).isFalse();
            assertThat(raffleTicketRepository.existsByTicketNumberAndRaffleId("T-999", raffleA.getId())).isFalse();
        }
    }

    // ==================== contadores por usuario ====================

    @Nested
    @DisplayName("contadores por usuario")
    class CountersByUser {

        @Test
        @DisplayName("countByTicketOwnerIdAndRaffleId cuenta solo los tickets del usuario en esa rifa")
        void countByOwnerAndRaffle() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            Raffle raffleA = persistRaffle("Rifa contador A", RaffleStatus.ACTIVE, now().plusDays(10));
            Raffle raffleB = persistRaffle("Rifa contador B", RaffleStatus.ACTIVE, now().plusDays(20));

            persistTicket(raffleA, owner, "C-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(raffleA, owner, "C-2", RaffleTicketSource.PURCHASE, 2L);
            persistTicket(raffleB, owner, "C-3", RaffleTicketSource.PURCHASE, 3L);
            persistTicket(raffleA, other, "C-4", RaffleTicketSource.PURCHASE, 4L);

            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleId(owner.getId(), raffleA.getId()))
                    .isEqualTo(2);
            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleId(owner.getId(), raffleB.getId()))
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("countByTicketOwnerIdAndRaffleIdAndStatus distingue por status del ticket")
        void countByOwnerRaffleAndStatus() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa contador status", RaffleStatus.ACTIVE, now().plusDays(10));

            persistTicket(raffle, owner, "S-1", RaffleTicketSource.PURCHASE, 1L);
            RaffleTicket expired = persistTicket(raffle, owner, "S-2", RaffleTicketSource.PURCHASE, 2L);
            setStatus(expired, RaffleTicketStatus.EXPIRED);

            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(
                    owner.getId(), raffle.getId(), RaffleTicketStatus.ACTIVE)).isEqualTo(1);
            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndStatus(
                    owner.getId(), raffle.getId(), RaffleTicketStatus.EXPIRED)).isEqualTo(1);
        }

        @Test
        @DisplayName("countByTicketOwnerIdAndStatus cuenta a través de todas las rifas del usuario")
        void countByOwnerAndStatusAcrossRaffles() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffleA = persistRaffle("Rifa global A", RaffleStatus.ACTIVE, now().plusDays(10));
            Raffle raffleB = persistRaffle("Rifa global B", RaffleStatus.ACTIVE, now().plusDays(20));

            persistTicket(raffleA, owner, "G-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(raffleB, owner, "G-2", RaffleTicketSource.PURCHASE, 2L);

            assertThat(raffleTicketRepository.countByTicketOwnerIdAndStatus(owner.getId(), RaffleTicketStatus.ACTIVE))
                    .isEqualTo(2);
            assertThat(raffleTicketRepository.countByTicketOwnerIdAndStatus(owner.getId(), RaffleTicketStatus.EXPIRED))
                    .isEqualTo(0);
        }

        @Test
        @DisplayName("countWinnerTicketsByUserId cuenta únicamente los tickets marcados isWinner=true")
        void countWinnerTickets() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa ganadora", RaffleStatus.ACTIVE, now().plusDays(10));

            RaffleTicket winner = persistTicket(raffle, owner, "W-1", RaffleTicketSource.PURCHASE, 1L);
            setWinner(winner, true);
            persistTicket(raffle, owner, "W-2", RaffleTicketSource.PURCHASE, 2L);

            assertThat(raffleTicketRepository.countWinnerTicketsByUserId(owner.getId())).isEqualTo(1);
        }

        @Test
        @DisplayName("countByTicketOwnerIdAndRaffleIdAndSource distingue por fuente")
        void countByOwnerRaffleAndSource() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa por fuente", RaffleStatus.ACTIVE, now().plusDays(10));

            persistTicket(raffle, owner, "F-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(raffle, owner, "F-2", RaffleTicketSource.DAILY_LOGIN, 2L);
            persistTicket(raffle, owner, "F-3", RaffleTicketSource.DAILY_LOGIN, 3L);

            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndSource(
                    owner.getId(), raffle.getId(), RaffleTicketSource.PURCHASE)).isEqualTo(1);
            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndSource(
                    owner.getId(), raffle.getId(), RaffleTicketSource.DAILY_LOGIN)).isEqualTo(2);
            assertThat(raffleTicketRepository.countByTicketOwnerIdAndRaffleIdAndSource(
                    owner.getId(), raffle.getId(), RaffleTicketSource.REFERRAL)).isEqualTo(0);
        }
    }

    // ==================== countTicketsByTicketOwnerGroupedByRaffle ====================

    @Nested
    @DisplayName("countTicketsByTicketOwnerGroupedByRaffle")
    class CountTicketsGroupedByRaffle {

        @Test
        @DisplayName("agrupa por rifa, solo cuenta tickets ACTIVE, y ordena por drawDate desc")
        void groupsByRaffleCountingOnlyActiveOrderedByDrawDateDesc() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle earlier = persistRaffle("Rifa temprana", RaffleStatus.ACTIVE, now().plusDays(5));
            Raffle later = persistRaffle("Rifa tardía", RaffleStatus.ACTIVE, now().plusDays(15));

            persistTicket(earlier, owner, "GR-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(earlier, owner, "GR-2", RaffleTicketSource.PURCHASE, 2L);
            RaffleTicket expiredInEarlier = persistTicket(earlier, owner, "GR-3", RaffleTicketSource.PURCHASE, 3L);
            setStatus(expiredInEarlier, RaffleTicketStatus.EXPIRED);

            persistTicket(later, owner, "GR-4", RaffleTicketSource.PURCHASE, 4L);

            List<Object[]> rows = raffleTicketRepository.countTicketsByTicketOwnerGroupedByRaffle(owner.getId());

            assertThat(rows).hasSize(2);

            // Orden: drawDate DESC -> "later" primero.
            Object[] firstRow = rows.get(0);
            assertThat(firstRow[0]).isEqualTo(later.getId());
            assertThat(firstRow[1]).isEqualTo("Rifa tardía");
            assertThat(firstRow[2]).isEqualTo(RaffleType.STANDARD);
            assertThat(((Number) firstRow[3]).longValue()).isEqualTo(1L);
            assertThat(firstRow[4]).isNotNull();
            assertThat(firstRow[5]).isEqualTo(RaffleStatus.ACTIVE);

            Object[] secondRow = rows.get(1);
            assertThat(secondRow[0]).isEqualTo(earlier.getId());
            // Solo los 2 tickets ACTIVE se cuentan, el EXPIRED queda excluido.
            assertThat(((Number) secondRow[3]).longValue()).isEqualTo(2L);
        }
    }

    // ==================== findUserTicketsByRaffle / findUserWinnerTickets ====================

    @Nested
    @DisplayName("findUserTicketsByRaffle y findUserWinnerTickets")
    class UserTicketQueries {

        @Test
        @DisplayName("findUserTicketsByRaffle pagina los tickets del usuario en esa rifa")
        void findUserTicketsByRafflePages() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            ConsumerDetails other = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa mis tickets", RaffleStatus.ACTIVE, now().plusDays(10));

            persistTicket(raffle, owner, "MT-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(raffle, owner, "MT-2", RaffleTicketSource.PURCHASE, 2L);
            persistTicket(raffle, other, "MT-3", RaffleTicketSource.PURCHASE, 3L);

            Page<RaffleTicket> page = raffleTicketRepository.findUserTicketsByRaffle(
                    owner.getId(), raffle.getId(), PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
            assertThat(page.getContent()).extracting(RaffleTicket::getTicketNumber)
                    .containsExactlyInAnyOrder("MT-1", "MT-2");
        }

        @Test
        @DisplayName("findUserWinnerTickets solo trae tickets marcados isWinner=true")
        void findUserWinnerTicketsOnlyWinners() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa mis ganadores", RaffleStatus.ACTIVE, now().plusDays(10));

            RaffleTicket winner = persistTicket(raffle, owner, "MW-1", RaffleTicketSource.PURCHASE, 1L);
            setWinner(winner, true);
            persistTicket(raffle, owner, "MW-2", RaffleTicketSource.PURCHASE, 2L);

            Page<RaffleTicket> page = raffleTicketRepository.findUserWinnerTickets(owner.getId(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(RaffleTicket::getTicketNumber).containsExactly("MW-1");
        }
    }

    // ==================== findRaffleTicketsWithFilters ====================

    @Nested
    @DisplayName("findRaffleTicketsWithFilters")
    class FindRaffleTicketsWithFilters {

        @Test
        @DisplayName("sin filtros opcionales trae todos los tickets de la rifa")
        void noFiltersReturnsAll() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa filtros admin", RaffleStatus.ACTIVE, now().plusDays(10));
            persistTicket(raffle, owner, "AF-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(raffle, owner, "AF-2", RaffleTicketSource.DAILY_LOGIN, 2L);

            Page<RaffleTicket> page = raffleTicketRepository.findRaffleTicketsWithFilters(
                    raffle.getId(), null, null, null, PageRequest.of(0, 10));

            assertThat(page.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("filtra por status y por source cuando se especifican")
        void filtersByStatusAndSource() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa filtros status source", RaffleStatus.ACTIVE, now().plusDays(10));
            persistTicket(raffle, owner, "AF-3", RaffleTicketSource.PURCHASE, 3L);
            RaffleTicket expired = persistTicket(raffle, owner, "AF-4", RaffleTicketSource.DAILY_LOGIN, 4L);
            setStatus(expired, RaffleTicketStatus.EXPIRED);

            Page<RaffleTicket> byStatus = raffleTicketRepository.findRaffleTicketsWithFilters(
                    raffle.getId(), RaffleTicketStatus.EXPIRED, null, null, PageRequest.of(0, 10));
            assertThat(byStatus.getContent()).extracting(RaffleTicket::getTicketNumber).containsExactly("AF-4");

            Page<RaffleTicket> bySource = raffleTicketRepository.findRaffleTicketsWithFilters(
                    raffle.getId(), null, RaffleTicketSource.PURCHASE, null, PageRequest.of(0, 10));
            assertThat(bySource.getContent()).extracting(RaffleTicket::getTicketNumber).containsExactly("AF-3");
        }

        @Test
        @DisplayName("filtra por issuedAt mínimo cuando se especifica")
        void filtersByIssuedAtMinimum() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa filtros issuedAt", RaffleStatus.ACTIVE, now().plusDays(10));
            persistTicket(raffle, owner, "AF-5", RaffleTicketSource.PURCHASE, 5L);

            Page<RaffleTicket> future = raffleTicketRepository.findRaffleTicketsWithFilters(
                    raffle.getId(), null, null, now().plusDays(1), PageRequest.of(0, 10));
            Page<RaffleTicket> past = raffleTicketRepository.findRaffleTicketsWithFilters(
                    raffle.getId(), null, null, now().minusDays(1), PageRequest.of(0, 10));

            assertThat(future.getContent()).isEmpty();
            assertThat(past.getContent()).extracting(RaffleTicket::getTicketNumber).containsExactly("AF-5");
        }
    }

    // ==================== existsByTicketOwnerIdAndSourceAndSourceId ====================

    @Nested
    @DisplayName("existsByTicketOwnerIdAndSourceAndSourceId")
    class ExistsByOwnerSourceAndSourceId {

        @Test
        @DisplayName("detecta idempotencia: mismo owner/source/sourceId ya emitido")
        void detectsAlreadyIssued() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa idempotencia", RaffleStatus.ACTIVE, now().plusDays(10));
            persistTicket(raffle, owner, "ID-1", RaffleTicketSource.PURCHASE, 555L);

            assertThat(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                    owner.getId(), RaffleTicketSource.PURCHASE, 555L)).isTrue();
            assertThat(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                    owner.getId(), RaffleTicketSource.PURCHASE, 556L)).isFalse();
            assertThat(raffleTicketRepository.existsByTicketOwnerIdAndSourceAndSourceId(
                    owner.getId(), RaffleTicketSource.DAILY_LOGIN, 555L)).isFalse();
        }
    }

    // ==================== findByRaffleIdAndStatus ====================

    @Nested
    @DisplayName("findByRaffleIdAndStatus")
    class FindByRaffleIdAndStatus {

        @Test
        @DisplayName("trae solo los tickets ACTIVE de la rifa (los que participan en el sorteo)")
        void returnsOnlyMatchingStatusTickets() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa para sorteo", RaffleStatus.ACTIVE, now().plusDays(10));
            persistTicket(raffle, owner, "DR-1", RaffleTicketSource.PURCHASE, 1L);
            RaffleTicket expired = persistTicket(raffle, owner, "DR-2", RaffleTicketSource.PURCHASE, 2L);
            setStatus(expired, RaffleTicketStatus.EXPIRED);

            List<RaffleTicket> active = raffleTicketRepository.findByRaffleIdAndStatus(raffle.getId(), RaffleTicketStatus.ACTIVE);
            List<RaffleTicket> expiredList = raffleTicketRepository.findByRaffleIdAndStatus(raffle.getId(), RaffleTicketStatus.EXPIRED);

            assertThat(active).extracting(RaffleTicket::getTicketNumber).containsExactly("DR-1");
            assertThat(expiredList).extracting(RaffleTicket::getTicketNumber).containsExactly("DR-2");
        }
    }

    // ==================== expireTicketsByRaffle ====================

    @Nested
    @DisplayName("expireTicketsByRaffle")
    class ExpireTicketsByRaffle {

        @Test
        @DisplayName("hace UPDATE masivo a EXPIRED y retorna el número de filas afectadas; el estado real en BD queda EXPIRED")
        void bulkExpiresActiveTicketsAndReturnsAffectedCount() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa a expirar", RaffleStatus.ACTIVE, now().plusDays(10));
            RaffleTicket t1 = persistTicket(raffle, owner, "EX-1", RaffleTicketSource.PURCHASE, 1L);
            RaffleTicket t2 = persistTicket(raffle, owner, "EX-2", RaffleTicketSource.PURCHASE, 2L);
            persistTicket(raffle, owner, "EX-3", RaffleTicketSource.PURCHASE, 3L);

            ZonedDateTime expiredAt = now();
            int affected = raffleTicketRepository.expireTicketsByRaffle(raffle.getId(), expiredAt);

            assertThat(affected).isEqualTo(3);

            // Sin clearAutomatically/flushAutomatically en el @Modifying, el UPDATE
            // masivo va directo a BD por SQL nativo y NO sincroniza las entidades ya
            // cargadas en el contexto de persistencia: t1 sigue reportando ACTIVE en
            // memoria hasta que se limpie o refresque el contexto.
            assertThat(t1.getStatus()).isEqualTo(RaffleTicketStatus.ACTIVE);

            em.clear();

            RaffleTicket reloaded1 = raffleTicketRepository.findById(t1.getId()).orElseThrow();
            RaffleTicket reloaded2 = raffleTicketRepository.findById(t2.getId()).orElseThrow();

            assertThat(reloaded1.getStatus()).isEqualTo(RaffleTicketStatus.EXPIRED);
            assertThat(reloaded1.getUsedAt()).isNotNull();
            assertThat(reloaded2.getStatus()).isEqualTo(RaffleTicketStatus.EXPIRED);
        }

        @Test
        @DisplayName("no afecta tickets que ya no están ACTIVE ni de otras rifas")
        void doesNotAffectNonActiveOrOtherRaffleTickets() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffleA = persistRaffle("Rifa a expirar A", RaffleStatus.ACTIVE, now().plusDays(10));
            Raffle raffleB = persistRaffle("Rifa a expirar B", RaffleStatus.ACTIVE, now().plusDays(20));

            RaffleTicket alreadyExpired = persistTicket(raffleA, owner, "EX-4", RaffleTicketSource.PURCHASE, 4L);
            setStatus(alreadyExpired, RaffleTicketStatus.EXPIRED);
            persistTicket(raffleB, owner, "EX-5", RaffleTicketSource.PURCHASE, 5L);

            int affected = raffleTicketRepository.expireTicketsByRaffle(raffleA.getId(), now());

            assertThat(affected).isEqualTo(0);

            em.clear();
            RaffleTicket reloadedB = raffleTicketRepository.findById(
                    raffleTicketRepository.findByRaffleIdAndStatus(raffleB.getId(), RaffleTicketStatus.ACTIVE)
                            .get(0).getId()).orElseThrow();
            assertThat(reloadedB.getStatus()).isEqualTo(RaffleTicketStatus.ACTIVE);
        }
    }

    // ==================== countTicketsBySource ====================

    @Nested
    @DisplayName("countTicketsBySource")
    class CountTicketsBySource {

        @Test
        @DisplayName("agrupa el conteo de tickets de la rifa por source")
        void groupsCountBySource() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            Raffle raffle = persistRaffle("Rifa stats por fuente", RaffleStatus.ACTIVE, now().plusDays(10));
            persistTicket(raffle, owner, "SRC-1", RaffleTicketSource.PURCHASE, 1L);
            persistTicket(raffle, owner, "SRC-2", RaffleTicketSource.PURCHASE, 2L);
            persistTicket(raffle, owner, "SRC-3", RaffleTicketSource.DAILY_LOGIN, 3L);

            List<Object[]> rows = raffleTicketRepository.countTicketsBySource(raffle.getId());

            assertThat(rows).hasSize(2);
            for (Object[] row : rows) {
                RaffleTicketSource source = (RaffleTicketSource) row[0];
                long count = ((Number) row[1]).longValue();
                if (source == RaffleTicketSource.PURCHASE) {
                    assertThat(count).isEqualTo(2L);
                } else if (source == RaffleTicketSource.DAILY_LOGIN) {
                    assertThat(count).isEqualTo(1L);
                }
            }
        }
    }
}
