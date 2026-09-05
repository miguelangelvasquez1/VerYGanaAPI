package com.verygana2.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.verygana2.dtos.commercial.report.AdsReportResponseDTO;
import com.verygana2.dtos.commercial.report.GamesReportResponseDTO;
import com.verygana2.dtos.commercial.report.PageVisitsReportResponseDTO;
import com.verygana2.dtos.commercial.report.SurveysReportResponseDTO;
import com.verygana2.services.commercial.CommercialReportServiceImpl;

import jakarta.persistence.EntityManager;

/**
 * Ejecuta el seed real {@code db/seed/test/test-commercial-metrics.sql} contra
 * H2 (modo MySQL) sobre una mini-fixture y verifica que:
 *  1. corre sin errores de SQL,
 *  2. es idempotente (segunda corrida no duplica),
 *  3. los 4 paneles de {@code /commercials/report/*} devuelven datos.
 */
@DataJpaTest(properties = {
        "spring.profiles.active=test",
        "spring.datasource.url=jdbc:h2:mem:commercial-metrics-seed-it;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.jakarta.persistence.validation.mode=none"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(CommercialReportServiceImpl.class)
@DisplayName("Seed test-commercial-metrics.sql (integración H2)")
class CommercialMetricsSeedIntegrationTest {

    private static final ZoneId ZONE = ZoneId.of("America/Bogota");
    private static final long STANDARD_ID = 5001L;
    private static final long PREMIUM_ID = 5002L;

    @Autowired private EntityManager em;
    @Autowired private DataSource dataSource;
    @Autowired private PlatformTransactionManager txManager;
    @Autowired private CommercialReportServiceImpl reportService;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("corre, es idempotente y llena los 4 paneles")
    void runsIdempotentlyAndFillsPanels() {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        tx.executeWithoutResult(s -> buildFixture());

        runSeed();
        long adLikes1 = count(tx, "ad_likes");
        long sessions1 = count(tx, "survey_sessions");
        long games1 = count(tx, "game_sessions");
        long visits1 = count(tx, "commercial_page_visits");

        assertThat(adLikes1).as("ad_likes sembrados").isGreaterThan(50);
        assertThat(sessions1).as("survey_sessions sembradas").isGreaterThan(150);
        assertThat(games1).as("game_sessions sembradas").isGreaterThan(200);
        assertThat(visits1).as("commercial_page_visits sembradas").isEqualTo(200);
        assertThat(count(tx, "surveys")).isEqualTo(11);
        assertThat(count(tx, "ads")).isEqualTo(22); // 10 premium fixture + 12 standard seed

        // Segunda corrida: idempotente.
        runSeed();
        assertThat(count(tx, "ad_likes")).isEqualTo(adLikes1);
        assertThat(count(tx, "survey_sessions")).isEqualTo(sessions1);
        assertThat(count(tx, "game_sessions")).isEqualTo(games1);
        assertThat(count(tx, "commercial_page_visits")).isEqualTo(visits1);
        assertThat(count(tx, "surveys")).isEqualTo(11);

        // Los 4 paneles del comercial PREMIUM devuelven datos.
        ZonedDateTime from = LocalDate.now(ZONE).minusDays(30).atStartOfDay(ZONE);
        ZonedDateTime to = LocalDate.now(ZONE).plusDays(1).atStartOfDay(ZONE);

        AdsReportResponseDTO ads = reportService.getAdsReport(PREMIUM_ID, from, to);
        assertThat(ads.summary().totalAds()).isEqualTo(10);
        assertThat(ads.summary().interactions()).isGreaterThan(0);
        assertThat(ads.interactionsByDay()).isNotEmpty();

        SurveysReportResponseDTO surveys = reportService.getSurveysReport(PREMIUM_ID, from, to);
        assertThat(surveys.summary().totalSurveys()).isEqualTo(5);
        assertThat(surveys.summary().completedSessions()).isGreaterThan(0);
        assertThat(surveys.summary().rewardPaidCents()).isGreaterThan(0);

        GamesReportResponseDTO gamesR = reportService.getGamesReport(PREMIUM_ID, from, to);
        assertThat(gamesR.summary().totalCampaigns()).isEqualTo(4);
        assertThat(gamesR.summary().sessionsPlayed()).isGreaterThan(0);
        assertThat(gamesR.summary().uniquePlayers()).isGreaterThan(0);

        PageVisitsReportResponseDTO pv = reportService.getPageVisitsReport(PREMIUM_ID, from, to);
        assertThat(pv.summary().totalVisits()).isGreaterThan(0);
        assertThat(pv.summary().uniqueVisitors()).isGreaterThan(0);
        assertThat(pv.visitsByAd()).isNotEmpty();
        // Regresión: con el seed hay ~200 visitas pero solo 6 consumers distintos, muchas más
        // que likes — conversionRatePct debe seguir acotado en [0, 100].
        assertThat(pv.summary().conversionRatePct()).isNotNull().isBetween(0.0, 100.0);

        // Y el comercial STANDARD también (ads/surveys/games).
        AdsReportResponseDTO stdAds = reportService.getAdsReport(STANDARD_ID, from, to);
        assertThat(stdAds.summary().totalAds()).isEqualTo(12);
        assertThat(stdAds.summary().pausedAds()).isEqualTo(2);
        assertThat(stdAds.summary().completedAds()).isEqualTo(3);
        assertThat(stdAds.perAd()).hasSize(12);

        GamesReportResponseDTO stdGames = reportService.getGamesReport(STANDARD_ID, from, to);
        assertThat(stdGames.summary().sessionsPlayed()).isGreaterThan(0);
    }

    private void runSeed() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource("db/seed/test/test-commercial-metrics.sql"));
        DatabasePopulatorUtils.execute(populator, dataSource);
    }

    private long count(TransactionTemplate tx, String table) {
        return tx.execute(s -> ((Number) em.createNativeQuery("SELECT COUNT(*) FROM " + table)
                .getSingleResult()).longValue());
    }

    // ==================== fixture ====================

    private void buildFixture() {
        exec("INSERT INTO department (code, name) VALUES ('63', 'Quindío')");
        exec("INSERT INTO municipality (code, name, department_code) VALUES ('63001', 'Armenia', '63')");

        commercialUser(STANDARD_ID, "comercial@verygana.com", "Empresa Demo S.A.S");
        commercialUser(PREMIUM_ID, "comercial-premium@verygana.com", "Ecosistema Premium S.A.S");

        for (int i = 0; i < 6; i++) {
            long id = 6000L + i;
            String email = i == 0 ? "consumer@verygana.com" : "consumer" + i + "@verygana.com";
            exec("INSERT INTO users (id, email, phone_number, password, role, user_state, registered_date, public_id) "
                    + "VALUES (" + id + ", '" + email + "', '30010000" + (10 + i) + "', 'x', 'CONSUMER', 'ACTIVE', CURRENT_TIMESTAMP, RANDOM_UUID())");
            exec("INSERT INTO user_details (user_id) VALUES (" + id + ")");
            exec("INSERT INTO consumer_details (user_id, user_hash, user_name, referral_code, municipality_code, "
                    + "name, last_name, department_name, municipality_name, has_pet, is_pep, ads_watched, daily_ad_count, "
                    + "age, gender, document_type, document_number) "
                    + "VALUES (" + id + ", 'hash-" + id + "', 'consumer_" + id + "', 'REF-" + id + "', '63001', "
                    + "'N', 'A', 'Quindío', 'Armenia', false, false, 0, 0, "
                    + "25, 'MALE', 'CC', 'DOC" + id + "')");
        }

        exec("INSERT INTO games (id, title, description, url, front_page_url, active, delivery_type, created_at) "
                + "VALUES (1, 'Seed Game', 'demo', 'games/seed', 'games/seed/index.html', true, 'PATH', CURRENT_TIMESTAMP)");
        exec("INSERT INTO game_config_definitions (id, game_id, version, json_schema, active, is_latest, created_at, "
                + "score_reward_factor, completion_reward_cents, max_reward_per_session_cents, average_reward_per_session_cents, average_duration_seconds) "
                + "VALUES (1, 1, 1, JSON '{}', true, true, CURRENT_TIMESTAMP, 50.0, 500000, 400000, 450000, 120)");

        for (int i = 1; i <= 8; i++) {
            exec("INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id, "
                    + "score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents, "
                    + "budget_cents, spent_cents, max_session_per_user_per_day, start_date, end_date, status, created_at, updated_at, version, "
                    + "sessions_played, completed_sessions, total_play_time_seconds, unique_players_count) "
                    + "VALUES (" + i + ", 1, 1, JSON '{\"type\":\"seed\"}', " + STANDARD_ID + ", "
                    + "50.0, 450000, 500000, 400000, 25000000, 0, 5, "
                    + "TIMESTAMPADD(DAY, -20, CURRENT_TIMESTAMP), TIMESTAMPADD(DAY, 20, CURRENT_TIMESTAMP), 'ACTIVE', "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0, 0, 0, 0, 0)");
        }

        for (int i = 0; i < 10; i++) {
            long adId = 9500L + i;
            exec("INSERT INTO ads (id, version, title, description, reward_per_like, max_likes, current_likes, "
                    + "status, created_at, updated_at, commercial_id, target_url) "
                    + "VALUES (" + adId + ", 0, 'Ad Premium " + i + "', 'demo', 8000, 200, 40, 'ACTIVE', "
                    + "TIMESTAMPADD(DAY, -20, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP, " + PREMIUM_ID + ", 'https://x')");
        }
    }

    private void commercialUser(long id, String email, String company) {
        exec("INSERT INTO users (id, email, phone_number, password, role, user_state, registered_date, public_id) "
                + "VALUES (" + id + ", '" + email + "', '3009" + id + "', 'x', 'COMMERCIAL', 'ACTIVE', CURRENT_TIMESTAMP, RANDOM_UUID())");
        exec("INSERT INTO user_details (user_id) VALUES (" + id + ")");
        exec("INSERT INTO commercial_details (user_id, company_name, is_pep) VALUES (" + id + ", '" + company + "', false)");
    }

    private void exec(String sql) {
        em.createNativeQuery(sql).executeUpdate();
    }
}
