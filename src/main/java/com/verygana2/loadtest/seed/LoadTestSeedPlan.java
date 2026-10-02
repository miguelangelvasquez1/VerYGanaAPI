package com.verygana2.loadtest.seed;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan del sembrado de volumen. Lógica pura, sin BD: dado el total de
 * usuarios calcula cuántos hay de cada rol y cuántas filas históricas lleva cada tabla.
 *
 * <p>La misma regla da la mezcla de usuarios virtuales: el personal interno es fijo
 * (5 diseñadores, 3 admins, 2 de cumplimiento) y el resto se reparte 95 % consumidores y
 * 5 % comerciales, con redondeo half-up en los comerciales para que los roles sumen siempre
 * el total.
 *
 * <p>Los multiplicadores son el único origen de verdad: el seeder los pasa a los scripts SQL
 * como variables de sesión {@code @lt_m_<clave>}, así que cambiarlos aquí cambia el volumen
 * sin tocar los scripts (y el informe los reporta).
 */
public record LoadTestSeedPlan(int totalUsers, int consumers, int commercials, int designers, int admins,
        int complianceOfficers) {

    public static final int DESIGNERS = 5;
    public static final int ADMINS = 3;
    public static final int COMPLIANCE_OFFICERS = 2;

    /** Porcentaje de comerciales sobre el resto (total menos personal interno). */
    private static final int COMMERCIAL_PERCENT = 5;

    /**
     * Multiplicadores de historial (~3 meses de uso). Por consumidor, por comercial y
     * globales, en un solo mapa para que el seeder los pase tal cual a los scripts.
     */
    private static final Map<String, Integer> MULTIPLIERS = buildMultipliers();

    public static LoadTestSeedPlan forTotalUsers(int totalUsers) {
        int staff = DESIGNERS + ADMINS + COMPLIANCE_OFFICERS;
        if (totalUsers < staff) {
            throw new IllegalArgumentException(
                    "El total de usuarios (" + totalUsers + ") es menor al personal interno (" + staff + ")");
        }
        int rest = totalUsers - staff;
        // round half up de rest * 5 %: (rest * 5 + 50) / 100 en enteros.
        int commercials = (rest * COMMERCIAL_PERCENT + 50) / 100;
        int consumers = rest - commercials;
        return new LoadTestSeedPlan(totalUsers, consumers, commercials, DESIGNERS, ADMINS, COMPLIANCE_OFFICERS);
    }

    public int internalStaff() {
        return designers + admins + complianceOfficers;
    }

    /**
     * Plan de un comercial según su número (1..N). Depende solo de {@code n mod 5}, no de rangos,
     * para que al crecer de A a B los comerciales ya sembrados conserven su plan: 40 % BASIC,
     * 40 % STANDARD y 20 % PREMIUM.
     */
    public static String planOfCommercial(int n) {
        return switch (Math.floorMod(n, 5)) {
            case 0, 1 -> "BASIC";
            case 2, 3 -> "STANDARD";
            default -> "PREMIUM";
        };
    }

    public Map<String, Integer> commercialsByPlan() {
        Map<String, Integer> byPlan = new LinkedHashMap<>();
        byPlan.put("BASIC", 0);
        byPlan.put("STANDARD", 0);
        byPlan.put("PREMIUM", 0);
        for (int n = 1; n <= commercials; n++) {
            byPlan.merge(planOfCommercial(n), 1, Integer::sum);
        }
        return byPlan;
    }

    public Map<String, Integer> multipliers() {
        return MULTIPLIERS;
    }

    /**
     * Filas esperadas por tabla tras el sembrado (sin contar los datos de referencia ni los
     * globales de rifas). Es lo que {@code stress-tests/db/check-seed.sql} contrasta.
     */
    public Map<String, Long> expectedHistoryRows() {
        Map<String, Long> rows = new LinkedHashMap<>();
        long c = consumers;
        long k = commercials;
        Map<String, Integer> m = MULTIPLIERS;
        Map<String, Integer> plans = commercialsByPlan();

        rows.put("consumer_preferences", c * m.get("consumer_preferences"));
        rows.put("ad_watch_session", c * m.get("ad_watch_sessions"));
        rows.put("ad_likes", c * m.get("ad_likes"));
        rows.put("key_wallets", c);
        rows.put("user_level_profile", c);
        rows.put("key_transactions", c * m.get("key_transactions"));
        rows.put("xp_key_transaction_log", c * m.get("xp_logs"));
        rows.put("game_sessions", c * m.get("game_sessions"));
        rows.put("game_session_metrics", c * m.get("game_sessions") * m.get("metrics_per_session"));
        rows.put("raffle_tickets", c * m.get("raffle_tickets"));
        rows.put("raffle_participations", c * m.get("raffle_participations"));
        rows.put("notifications", c * m.get("notifications"));
        rows.put("pet_sessions", c * m.get("pet_sessions"));
        rows.put("pet_player_saves", c);
        rows.put("purchases", c * m.get("purchases"));
        rows.put("purchase_items", c * m.get("purchase_items"));
        rows.put("survey_sessions", c * m.get("survey_sessions"));
        rows.put("survey_answers", c * m.get("survey_sessions") * m.get("questions_per_survey"));
        rows.put("survey_rewards", c * m.get("survey_sessions"));
        rows.put("commercial_page_visits", c * m.get("page_visits"));
        rows.put("refresh_tokens", c * m.get("refresh_tokens"));
        rows.put("audit_logs", c * m.get("audit_logs"));

        rows.put("ads", k * m.get("ads"));
        rows.put("ad_assets", k * m.get("ads"));
        rows.put("campaigns", k * m.get("campaigns"));
        rows.put("branding_requests", k * m.get("branding_requests"));
        rows.put("branding_request_comments", k * m.get("branding_requests") * m.get("branding_comments"));
        rows.put("surveys", k * m.get("surveys"));
        rows.put("survey_questions", k * m.get("surveys") * m.get("questions_per_survey"));
        rows.put("question_options",
                k * m.get("surveys") * m.get("questions_per_survey") * m.get("options_per_question"));
        rows.put("products", k * m.get("products"));
        rows.put("product_stock", k * m.get("products") * m.get("stock_per_product"));
        rows.put("budget_transactions", k * m.get("budget_transactions"));
        rows.put("wallets", k);
        rows.put("subscriptions", k);
        rows.put("investments", k * m.get("investments"));
        rows.put("commercial_onboarding", k);
        rows.put("commercial_contracts", k);
        rows.put("commercial_documents", k * m.get("documents"));
        rows.put("catalog_integration_requests", (long) plans.get("PREMIUM"));
        rows.put("prosperity_accounts", (long) plans.get("STANDARD"));
        return Collections.unmodifiableMap(rows);
    }

    /**
     * Variables de sesión que el seeder fija antes de correr los scripts: conteos del plan
     * ({@code lt_*}) y multiplicadores ({@code lt_m_*}).
     */
    public Map<String, Object> sessionVariables() {
        Map<String, Object> vars = new LinkedHashMap<>();
        vars.put("lt_total_users", totalUsers);
        vars.put("lt_consumers", consumers);
        vars.put("lt_commercials", commercials);
        vars.put("lt_designers", designers);
        vars.put("lt_admins", admins);
        vars.put("lt_compliance", complianceOfficers);
        MULTIPLIERS.forEach((key, value) -> vars.put("lt_m_" + key, value));
        return vars;
    }

    private static Map<String, Integer> buildMultipliers() {
        Map<String, Integer> m = new LinkedHashMap<>();
        // Por consumidor
        m.put("consumer_preferences", 3);
        m.put("ad_watch_sessions", 60);
        m.put("ad_likes", 50);
        m.put("key_transactions", 80);
        m.put("xp_logs", 40);
        m.put("game_sessions", 20);
        m.put("metrics_per_session", 3);
        m.put("raffle_tickets", 15);
        m.put("raffle_participations", 5);
        m.put("notifications", 40);
        m.put("pet_sessions", 6);
        m.put("purchases", 2);
        m.put("purchase_items", 3);
        m.put("survey_sessions", 3);
        m.put("page_visits", 4);
        m.put("refresh_tokens", 3);
        m.put("audit_logs", 15);
        // Por comercial
        m.put("ads", 6);
        m.put("campaigns", 2);
        m.put("branding_requests", 2);
        m.put("branding_comments", 2);
        m.put("surveys", 2);
        m.put("questions_per_survey", 5);
        m.put("options_per_question", 4);
        m.put("products", 8);
        m.put("stock_per_product", 25);
        m.put("budget_transactions", 40);
        m.put("investments", 3);
        m.put("documents", 3);
        // Globales
        m.put("audience_pool", 12);
        m.put("raffles_active", 20);
        m.put("raffles_completed", 50);
        m.put("pet_notifications", 10);
        m.put("impact_stories", 10);
        // Tesorería (centavos): la reserva respalda las llaves sembradas (hasta 5.000.000 por
        // consumidor) y el gasto de llaves la mueve a operación; los payouts salen de pagos pendientes.
        m.put("treasury_keys_reserve_cents_per_consumer", 5_000_000);
        m.put("treasury_payouts_pending_cents_per_commercial", 20_000_000);
        m.put("treasury_operations_cents_per_commercial", 10_000_000);
        return Collections.unmodifiableMap(m);
    }
}
