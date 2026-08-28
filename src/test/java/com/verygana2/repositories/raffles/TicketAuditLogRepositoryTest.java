package com.verygana2.repositories.raffles;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.verygana2.models.enums.raffles.AuditAction;
import com.verygana2.models.enums.raffles.DrawMethod;
import com.verygana2.models.enums.raffles.RaffleTicketSource;
import com.verygana2.models.enums.raffles.RaffleType;
import com.verygana2.models.raffles.Raffle;
import com.verygana2.models.raffles.RaffleTicket;
import com.verygana2.models.raffles.TicketAuditLog;
import com.verygana2.models.userDetails.ConsumerDetails;
import com.verygana2.testsupport.TestEntities;

import jakarta.persistence.EntityManager;

/**
 * Tests de integración H2 (modo MySQL) para TicketAuditLogRepository.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:ticket-audit-log-repo-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("TicketAuditLogRepository (integración H2)")
class TicketAuditLogRepositoryTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private TicketAuditLogRepository ticketAuditLogRepository;

    // ==================== HELPERS ====================

    private static ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private RaffleTicket persistTicket(ConsumerDetails owner, String ticketNumber) {
        Raffle raffle = new Raffle();
        raffle.setTitle("Rifa audit " + ticketNumber);
        raffle.setDescription("desc");
        raffle.setRaffleType(RaffleType.STANDARD);
        raffle.setStartDate(now().minusDays(1));
        raffle.setEndDate(now().plusDays(5));
        raffle.setDrawDate(now().plusDays(6));
        raffle.setDrawMethod(DrawMethod.SYSTEM_RANDOM);
        raffle.setCreatedBy(1L);
        em.persist(raffle);
        em.flush();

        RaffleTicket ticket = new RaffleTicket();
        ticket.setRaffle(raffle);
        ticket.setTicketOwner(owner);
        ticket.setTicketNumber(ticketNumber);
        ticket.setSource(RaffleTicketSource.PURCHASE);
        ticket.setSourceId(1L);
        em.persist(ticket);
        em.flush();
        return ticket;
    }

    private TicketAuditLog persistLog(RaffleTicket ticket, RaffleTicketSource sourceType, String ipAddress,
            ZonedDateTime createdAt) {
        TicketAuditLog log = new TicketAuditLog();
        log.setTicket(ticket);
        log.setAction(AuditAction.ISSUED);
        log.setSourceType(sourceType);
        log.setSourceId(1L);
        log.setIpAddress(ipAddress);
        em.persist(log);
        em.flush();

        if (createdAt != null) {
            log.setCreatedAt(createdAt);
            em.flush();
        }
        return log;
    }

    // ==================== findByTicketIdOrderByCreatedAtDesc ====================

    @Nested
    @DisplayName("findByTicketIdOrderByCreatedAtDesc")
    class FindByTicketIdOrderByCreatedAtDesc {

        @Test
        @DisplayName("trae los logs de un ticket ordenados del más reciente al más antiguo")
        void returnsLogsOrderedNewestFirst() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "AL-1");
            RaffleTicket otherTicket = persistTicket(owner, "AL-2");

            TicketAuditLog oldLog = persistLog(ticket, RaffleTicketSource.PURCHASE, "1.1.1.1", now().minusHours(2));
            TicketAuditLog newLog = persistLog(ticket, RaffleTicketSource.PURCHASE, "1.1.1.1", now().minusMinutes(1));
            persistLog(otherTicket, RaffleTicketSource.PURCHASE, "2.2.2.2", now());

            List<TicketAuditLog> logs = ticketAuditLogRepository.findByTicketIdOrderByCreatedAtDesc(ticket.getId());

            assertThat(logs).extracting(TicketAuditLog::getId).containsExactly(newLog.getId(), oldLog.getId());
        }
    }

    // ==================== findByAction ====================

    @Nested
    @DisplayName("findByAction")
    class FindByAction {

        /**
         * El método declara el parámetro como String, pero el campo mapeado
         * (action) es un enum @Enumerated(STRING). En esta versión de
         * Hibernate/Spring Data (validación estricta de tipos de parámetro en
         * QueryParameterBindingValidator), esto NO hace una conversión
         * implícita String→enum: cualquier valor String pasado, matchee o no
         * el nombre de una constante del enum, hace fallar el binding del
         * parámetro con InvalidDataAccessApiUsageException ANTES de ejecutar
         * SQL alguno. Se documenta el comportamiento real (verificado contra
         * H2) en vez de el comportamiento ingenuamente esperado.
         */
        @Test
        @DisplayName("un string que coincide con el nombre del enum igual falla: Hibernate valida el tipo de parámetro en bind, no el valor")
        void matchingActionStringStillThrowsDueToStrictParameterTypeValidation() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "ACT-1");
            persistLog(ticket, RaffleTicketSource.PURCHASE, "3.3.3.3", now());

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> ticketAuditLogRepository.findByAction("ISSUED", PageRequest.of(0, 10)))
                    .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                    .hasMessageContaining("did not match parameter type");
        }

        @Test
        @DisplayName("un string que NO coincide con ningún valor del enum también falla con la misma excepción de tipo, no con un resultado vacío")
        void nonMatchingActionStringAlsoThrowsSameTypeException() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "ACT-2");
            persistLog(ticket, RaffleTicketSource.PURCHASE, "3.3.3.3", now());

            org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> ticketAuditLogRepository.findByAction("NOT_A_REAL_ACTION", PageRequest.of(0, 10)))
                    .isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class)
                    .hasMessageContaining("did not match parameter type");
        }
    }

    // ==================== findBySourceType ====================

    @Nested
    @DisplayName("findBySourceType")
    class FindBySourceType {

        @Test
        @DisplayName("filtra los logs por su fuente")
        void filtersLogsBySourceType() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "SRC-1");
            persistLog(ticket, RaffleTicketSource.PURCHASE, "4.4.4.4", now());
            persistLog(ticket, RaffleTicketSource.DAILY_LOGIN, "4.4.4.5", now());

            Page<TicketAuditLog> purchaseLogs = ticketAuditLogRepository.findBySourceType(
                    RaffleTicketSource.PURCHASE, PageRequest.of(0, 10));
            Page<TicketAuditLog> loginLogs = ticketAuditLogRepository.findBySourceType(
                    RaffleTicketSource.DAILY_LOGIN, PageRequest.of(0, 10));
            Page<TicketAuditLog> referralLogs = ticketAuditLogRepository.findBySourceType(
                    RaffleTicketSource.REFERRAL, PageRequest.of(0, 10));

            assertThat(purchaseLogs.getContent()).hasSize(1);
            assertThat(loginLogs.getContent()).hasSize(1);
            assertThat(referralLogs.getContent()).isEmpty();
        }
    }

    // ==================== findLogsBetweenDates ====================

    @Nested
    @DisplayName("findLogsBetweenDates")
    class FindLogsBetweenDates {

        @Test
        @DisplayName("trae solo los logs dentro del rango de fechas, ordenados descendente")
        void returnsLogsWithinRangeOrderedDesc() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "BD-1");

            TicketAuditLog outsideBefore = persistLog(ticket, RaffleTicketSource.PURCHASE, "5.5.5.5", now().minusDays(10));
            TicketAuditLog inRangeOld = persistLog(ticket, RaffleTicketSource.PURCHASE, "5.5.5.5", now().minusDays(2));
            TicketAuditLog inRangeNew = persistLog(ticket, RaffleTicketSource.PURCHASE, "5.5.5.5", now().minusDays(1));
            TicketAuditLog outsideAfter = persistLog(ticket, RaffleTicketSource.PURCHASE, "5.5.5.5", now().plusDays(10));

            Page<TicketAuditLog> page = ticketAuditLogRepository.findLogsBetweenDates(
                    now().minusDays(3), now(), PageRequest.of(0, 10));

            assertThat(page.getContent()).extracting(TicketAuditLog::getId)
                    .containsExactly(inRangeNew.getId(), inRangeOld.getId());
            assertThat(outsideBefore).isNotNull();
            assertThat(outsideAfter).isNotNull();
        }
    }

    // ==================== findSuspiciousActivity ====================

    @Nested
    @DisplayName("findSuspiciousActivity")
    class FindSuspiciousActivity {

        @Test
        @DisplayName("agrupa por IP y solo trae las IPs cuyo conteo supera el umbral")
        void returnsOnlyIpsAboveThreshold() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "SUS-1");

            String suspiciousIp = "9.9.9.9";
            for (int i = 0; i < 5; i++) {
                persistLog(ticket, RaffleTicketSource.PURCHASE, suspiciousIp, now().minusMinutes(i));
            }

            String normalIp = "8.8.8.8";
            persistLog(ticket, RaffleTicketSource.PURCHASE, normalIp, now());

            List<Object[]> rows = ticketAuditLogRepository.findSuspiciousActivity(now().minusHours(1), 3L);

            assertThat(rows).hasSize(1);
            Object[] row = rows.get(0);
            assertThat(row[0]).isEqualTo(suspiciousIp);
            assertThat(((Number) row[1]).longValue()).isEqualTo(5L);
        }

        @Test
        @DisplayName("no trae nada si ninguna IP supera el umbral")
        void returnsEmptyWhenNoIpAboveThreshold() {
            ConsumerDetails owner = TestEntities.persistConsumer(em);
            RaffleTicket ticket = persistTicket(owner, "SUS-2");
            persistLog(ticket, RaffleTicketSource.PURCHASE, "7.7.7.7", now());
            persistLog(ticket, RaffleTicketSource.PURCHASE, "7.7.7.7", now());

            List<Object[]> rows = ticketAuditLogRepository.findSuspiciousActivity(now().minusHours(1), 5L);

            assertThat(rows).isEmpty();
        }
    }
}
