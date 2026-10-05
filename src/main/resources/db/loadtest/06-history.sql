-- ============================================================================
-- Sembrado de la prueba de carga · 06 · historial por usuario
--
-- Unos 3 meses de uso: los multiplicadores son @lt_m_* de LoadTestSeedPlan. Las fechas se
-- reparten en los 90 días anteriores para que los filtros por rango y los índices por fecha
-- trabajen como en producción. Montos en centavos.
--
-- Idempotente: cada bloque solo siembra a los usuarios que aún no tienen filas de esa tabla
-- (NOT EXISTS dentro del CTE `cons` / `comm`), así que correrlo de nuevo no duplica y crecer de
-- A a B solo agrega historial a los consumidores y comerciales nuevos. Lo que ya existe no se toca.
--
-- Las filas con crédito (ad_likes, game_sessions, survey_rewards) nacen con credited_amount y
-- issuance_settled = 1: así KeyIssuanceBackfillRunner y el job de liquidación de llaves no
-- reprocesan cientos de miles de filas sembradas.
-- ============================================================================

-- Totales de las tablas ordenadas que usan los bloques (se leen una sola vez)
SET @lt_ads = (SELECT COUNT(*) FROM ads WHERE title LIKE 'LT Ad %');
SET @lt_campaigns = (SELECT COUNT(*) FROM campaigns WHERE JSON_EXTRACT(config_data, '$.loadtest') IS NOT NULL);
SET @lt_surveys = (SELECT COUNT(*) FROM surveys WHERE title LIKE 'LT Survey %');
SET @lt_active_raffles = (SELECT COUNT(*) FROM raffles WHERE title LIKE 'LT Raffle A%');

-- ---------------------------------------------------------------------------
-- Anuncios vistos: las primeras @lt_m_ad_likes sesiones terminan en like (LIKED) sobre anuncios
-- distintos; el resto expira. La fórmula de anuncio es la misma que la de ad_likes.
-- ---------------------------------------------------------------------------
INSERT INTO ad_watch_session (id, expires_at, resume_count, started_at, status, version, ad_id, consumer_user_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_ad_watch_sessions - 1),
adr AS (
    SELECT a.id, a.commercial_id AS cid, a.reward_per_like AS reward, ROW_NUMBER() OVER (ORDER BY a.id) - 1 AS rn
    FROM ads a WHERE a.title LIKE 'LT Ad %'
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM ad_watch_session w WHERE w.consumer_user_id = u.id)
)
SELECT UUID_TO_BIN(UUID()), TIMESTAMPADD(MINUTE, 30, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW())), 0, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()),
       IF(q.s < @lt_m_ad_likes, 'LIKED', 'EXPIRED'), 0, a.id, c.uid
FROM cons c
JOIN seq q
JOIN adr a ON a.rn = IF(q.s < @lt_m_ad_likes, MOD(c.n + q.s, @lt_ads), MOD(c.n + q.s * 7, @lt_ads));

INSERT IGNORE INTO ad_likes (created_at, reward_amount, credited_amount, issuance_settled, consumer_user_id, ad_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_ad_likes - 1),
adr AS (
    SELECT a.id, a.commercial_id AS cid, a.reward_per_like AS reward, ROW_NUMBER() OVER (ORDER BY a.id) - 1 AS rn
    FROM ads a WHERE a.title LIKE 'LT Ad %'
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM ad_likes l WHERE l.consumer_user_id = u.id)
)
SELECT TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), a.reward, a.reward, 1, c.uid, a.id
FROM cons c
JOIN seq q
JOIN adr a ON a.rn = MOD(c.n + q.s, @lt_ads);

-- ---------------------------------------------------------------------------
-- Llaves: 80 % créditos por interacción, 10 % bono de referido y 10 % débitos de copago.
-- Los créditos vencen a los 90 días (en el futuro: el job de expiración no los toca todavía).
-- ---------------------------------------------------------------------------
INSERT INTO key_transactions (id, connectivity_keys_delta_cents, created_at, expires_at, expiry_processed,
                              purchase_keys_delta_cents, reason, reference_id, type, key_wallet_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_key_transactions - 1),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM key_transactions k WHERE k.key_wallet_id = u.public_id)
)
SELECT UUID_TO_BIN(UUID()),
       IF(MOD(q.s, 10) = 9, 0, 200 + MOD(c.n + q.s * 13, 600)),
       TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()),
       IF(MOD(q.s, 10) = 9, NULL, TIMESTAMPADD(DAY, 90, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()))),
       IF(MOD(q.s, 10) = 9, 1, 0),
       IF(MOD(q.s, 10) = 9, -(500 + MOD(c.n + q.s * 17, 1500)), 300 + MOD(c.n + q.s * 11, 1200)),
       'Movimiento ficticio de la prueba de carga',
       UUID_TO_BIN(UUID()),
       CASE WHEN MOD(q.s, 10) = 9 THEN 'DEBIT_COPAYMENT'
            WHEN MOD(q.s, 10) = 8 THEN 'CREDIT_REFERRAL_BONUS'
            ELSE 'CREDIT_INTERACTION' END,
       c.pid
FROM cons c
JOIN seq q;

INSERT INTO xp_key_transaction_log (activity_type, consumer_id, created_at, multiplier_applied, xp_earned)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_xp_logs - 1),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM xp_key_transaction_log x WHERE x.consumer_id = u.id)
)
SELECT ELT(1 + MOD(q.s, 5), 'GAME_PLAYED', 'PURCHASE', 'REFERRAL_ACTIVE', 'SURVEY_COMPLETED', 'VIDEO_WATCHED'),
       c.uid, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), 1.0, 10 + MOD(c.n + q.s * 7, 50)
FROM cons c
JOIN seq q;

-- ---------------------------------------------------------------------------
-- Partidas de juego y sus métricas (3 por partida)
-- ---------------------------------------------------------------------------
INSERT INTO game_sessions (coins_earned, credited_amount, issuance_settled, completed, device_platform, end_time,
                           play_time_seconds, reward_granted, score, session_token, start_time, user_hash,
                           campaign_id, consumer_id, game_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_game_sessions - 1),
cmp AS (
    SELECT cp.id, cp.game_id, ROW_NUMBER() OVER (ORDER BY cp.id) - 1 AS rn
    FROM campaigns cp WHERE JSON_EXTRACT(cp.config_data, '$.loadtest') IS NOT NULL
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM game_sessions g WHERE g.consumer_id = u.id)
)
SELECT 3000 + MOD(c.n * 17 + q.s * 31, 2500), 3000 + MOD(c.n * 17 + q.s * 31, 2500), 1, 1,
       ELT(1 + MOD(c.n + q.s, 3), 'PC', 'MOBILE', 'TABLET'),
       TIMESTAMPADD(SECOND, 60 + MOD(c.n + q.s * 13, 180), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW())),
       60 + MOD(c.n + q.s * 13, 180), 1, MOD(c.n * 37 + q.s * 101, 1000),
       CONCAT('lt-gs-', c.uid, '-', q.s), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), BIN_TO_UUID(c.pid), m.id, c.uid, m.game_id
FROM cons c
JOIN seq q
JOIN cmp m ON m.rn = MOD(c.n + q.s * 5, @lt_campaigns);

INSERT INTO game_session_metrics (metric_key, metric_type, metric_value, recorded_at, unit, session_id)
WITH RECURSIVE seq (m) AS (SELECT 0 UNION ALL SELECT m + 1 FROM seq WHERE m < @lt_m_metrics_per_session - 1)
SELECT ELT(1 + MOD(s.m, 3), 'score', 'level', 'duration'), 'INT', CAST(gs.score + s.m AS JSON),
       gs.end_time, ELT(1 + MOD(s.m, 3), 'pts', 'lvl', 's'), gs.id
FROM game_sessions gs
JOIN seq s
WHERE gs.session_token LIKE 'lt-gs-%'
  AND NOT EXISTS (SELECT 1 FROM game_session_metrics x WHERE x.session_id = gs.id);

-- ---------------------------------------------------------------------------
-- Rifas: boletos y participación en las rifas activas
-- ---------------------------------------------------------------------------
INSERT INTO raffle_tickets (is_winner, issued_at, source, source_id, status, ticket_number, raffle_id, ticket_owner_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_raffle_tickets - 1),
rfa AS (
    SELECT r.id, ROW_NUMBER() OVER (ORDER BY r.id) - 1 AS rn FROM raffles r WHERE r.title LIKE 'LT Raffle A%'
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM raffle_tickets t WHERE t.ticket_owner_id = u.id AND t.ticket_number LIKE 'LT-%')
)
SELECT 0, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), ELT(1 + MOD(q.s, 3), 'DAILY_LOGIN', 'PURCHASE', 'REFERRAL'), q.s, 'ACTIVE',
       CONCAT('LT-', c.uid, '-', q.s), r.id, c.uid
FROM cons c
JOIN seq q
JOIN rfa r ON r.rn = MOD(c.n + q.s, @lt_active_raffles);

INSERT IGNORE INTO raffle_participations (first_participation_at, last_participation_at, tickets_count, consumer_id, raffle_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_raffle_participations - 1),
rfa AS (
    SELECT r.id, ROW_NUMBER() OVER (ORDER BY r.id) - 1 AS rn FROM raffles r WHERE r.title LIKE 'LT Raffle A%'
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM raffle_participations p WHERE p.consumer_id = u.id)
)
SELECT TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), TIMESTAMPADD(HOUR, 1 + MOD(c.n + q.s, 48), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW())), 1 + MOD(c.n + q.s, 5), c.uid, r.id
FROM cons c
JOIN seq q
JOIN rfa r ON r.rn = MOD(c.n + q.s * 3, @lt_active_raffles);

-- ---------------------------------------------------------------------------
-- Notificaciones (el 75 % leídas), mascotas, páginas visitadas, sesiones, auditoría
-- ---------------------------------------------------------------------------
INSERT INTO notifications (created_at, date_sent, is_read, message, title, type, user_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_notifications - 1),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM notifications x WHERE x.user_id = u.id)
)
SELECT TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), IF(MOD(q.s, 4) < 3, 1, 0), 'Notificación ficticia de la prueba de carga',
       CONCAT('LT Notificación ', q.s), 'IN_APP_NOTIFICATION', c.uid
FROM cons c
JOIN seq q;

INSERT INTO pet_sessions (expired, session_token, start_time, user_hash, consumer_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_pet_sessions - 1),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM pet_sessions x WHERE x.consumer_id = u.id)
)
SELECT 1, CONCAT('lt-ps-', c.uid, '-', q.s), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), BIN_TO_UUID(c.pid), c.uid
FROM cons c
JOIN seq q;

INSERT INTO pet_player_saves (consumer_id, data, updated_at)
SELECT u.id, '{"loadtest": true}', TIMESTAMPADD(HOUR, -MOD(u.id, 72), NOW())
FROM users u
WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO commercial_page_visits (created_at, source, target_url, user_hash, ad_id, commercial_id, consumer_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_page_visits - 1),
adr AS (
    SELECT a.id, a.commercial_id AS cid, a.reward_per_like AS reward, ROW_NUMBER() OVER (ORDER BY a.id) - 1 AS rn
    FROM ads a WHERE a.title LIKE 'LT Ad %'
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM commercial_page_visits v WHERE v.consumer_id = u.id)
)
SELECT TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), 'AD', 'https://empresa.loadtest.invalid/landing', BIN_TO_UUID(c.pid), a.id, a.cid, c.uid
FROM cons c
JOIN seq q
JOIN adr a ON a.rn = MOD(c.n + q.s * 11, @lt_ads);

-- Refresh tokens ya revocados (sesiones viejas): la base guarda la huella SHA-256, no el token.
-- jti único determinista, así que INSERT IGNORE basta
INSERT IGNORE INTO refresh_tokens (created_at, expires_at, jti, last_used_at, revoked, token_hash, username)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_refresh_tokens - 1)
SELECT TIMESTAMPADD(DAY, -(1 + q.s), NOW()), TIMESTAMPADD(DAY, 6 - q.s, NOW()),
       CONCAT('lt-jti-', u.id, '-', q.s), TIMESTAMPADD(DAY, -(1 + q.s), NOW()), 1,
       SHA2(CONCAT('loadtest-token-', u.id, '-', q.s), 256), u.email
FROM users u
JOIN seq q
WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid';

INSERT INTO audit_logs (action, category, created_at, description, entity_id, entity_type, level, success, user_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_audit_logs - 1),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM audit_logs x WHERE x.user_id = u.id AND x.action = 'LT_SEED_EVENT')
)
SELECT 'LT_SEED_EVENT', 'LOADTEST', TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), 'Evento ficticio de la prueba de carga', c.uid, 'USER', 'INFO', 1, c.uid
FROM cons c
JOIN seq q;

-- ---------------------------------------------------------------------------
-- Compras. Cada ítem vale 20.000 COP (2.000.000 centavos), comisión 10 % y 20 % pagado con llaves;
-- el reparto en compras es 2 ítems en la primera y el resto en la última (3 ítems en 2 compras).
-- Cada ítem lleva además un delivered_code ficticio (placeholder RAW: que el seeder cifra en Java,
-- como el stock) para que GET /purchaseItems/{id}/delivered-code mida el camino feliz.
-- Cada ítem lleva un código de stock distinto: el consumidor n toma los códigos de rango
-- (n-1) * @lt_m_purchase_items + j, y quedan VENDIDOS.
-- ---------------------------------------------------------------------------
INSERT INTO purchases (cash_cents, commission_cents, completed_at, created_at, delivery_email_verified,
                       keys_value_cents, net_to_commercials_cents, reference_id, status, total_cents,
                       updated_at, consumer_id)
WITH RECURSIVE seq (k) AS (SELECT 0 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_purchases - 1),
-- Ítems de cada compra: 2 en todas menos la última, que lleva el resto
pk AS (
    SELECT k, IF(k < @lt_m_purchases - 1, 2, @lt_m_purchase_items - 2 * (@lt_m_purchases - 1)) AS items FROM seq
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM purchases x WHERE x.consumer_id = u.id)
)
SELECT q.items * 2000000 - q.items * 2000000 * 20 DIV 100, q.items * 200000,
       TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.k * 104729, 7776000), NOW()),
       TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.k * 104729, 7776000), NOW()), 1,
       q.items * 2000000 * 20 DIV 100, q.items * 1800000, CONCAT('LT-PUR-', c.n, '-', q.k), 'COMPLETED',
       q.items * 2000000, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.k * 104729, 7776000), NOW()), c.uid
FROM cons c
JOIN pk q;

INSERT INTO purchase_items (commission_cents, commission_pct_applied, created_at, delivered_at, max_keys_pct_at_purchase,
                            net_to_commercial_cents, product_name_snapshot, status, subtotal_cents, unit_price_cents,
                            product_stock_id, product_id, purchase_id, claim_attempts, claimed_at, commercial_id,
                            commercial_activity_type_at_purchase, delivered_code)
WITH RECURSIVE seq (j) AS (SELECT 0 UNION ALL SELECT j + 1 FROM seq WHERE j < @lt_m_purchase_items - 1),
stk AS (
    SELECT ps.id AS stock_id, p.id AS product_id, p.commercial_id AS cid, p.name AS pname,
           ROW_NUMBER() OVER (ORDER BY ps.id) - 1 AS rn
    FROM product_stock ps
    JOIN products p ON p.id = ps.product_id
    WHERE p.name LIKE 'LT Product %'
),
pur AS (
    SELECT pu.id AS pid, pu.created_at AS created,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(pu.reference_id, '-', 3), '-', -1) AS UNSIGNED) AS n,
           CAST(SUBSTRING_INDEX(pu.reference_id, '-', -1) AS UNSIGNED) AS k
    FROM purchases pu
    WHERE pu.reference_id LIKE 'LT-PUR-%'
      AND NOT EXISTS (SELECT 1 FROM purchase_items x WHERE x.purchase_id = pu.id)
)
SELECT 200000, 10, pr.created, TIMESTAMPADD(MINUTE, 5, pr.created), 20, 1800000, st.pname, 'CLAIMED', 2000000, 2000000,
       st.stock_id, st.product_id, pr.pid, 0, TIMESTAMPADD(MINUTE, 6, pr.created), st.cid, 'PRODUCTS',
       CONCAT('RAW:LT-DELIVERED-', st.stock_id)
FROM pur pr
JOIN seq s ON LEAST(s.j DIV 2, @lt_m_purchases - 1) = pr.k
JOIN stk st ON st.rn = (pr.n - 1) * @lt_m_purchase_items + s.j;

UPDATE product_stock ps
JOIN purchase_items pi ON pi.product_stock_id = ps.id
JOIN purchases pu ON pu.id = pi.purchase_id AND pu.reference_id LIKE 'LT-PUR-%'
SET ps.status = 'SOLD', ps.sold_at = pi.created_at, ps.updated_at = pi.created_at
WHERE ps.status = 'AVAILABLE';

-- ---------------------------------------------------------------------------
-- Encuestas respondidas: 3 sesiones COMPLETED por consumidor, una respuesta por pregunta y su premio
-- ---------------------------------------------------------------------------
INSERT INTO survey_sessions (completed_at, expires_at, started_at, status, version, consumer_id, survey_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_survey_sessions - 1),
svr AS (
    SELECT sv.id, ROW_NUMBER() OVER (ORDER BY sv.id) - 1 AS rn FROM surveys sv WHERE sv.title LIKE 'LT Survey %'
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM survey_sessions x WHERE x.consumer_id = u.id)
)
SELECT TIMESTAMPADD(MINUTE, 5, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW())), TIMESTAMPADD(MINUTE, 30, TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW())), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), 'COMPLETED', 0, c.uid, v.id
FROM cons c
JOIN seq q
JOIN svr v ON v.rn = MOD(c.n + q.s * 7, @lt_surveys);

INSERT INTO survey_answers (question_id, selected_option_id, session_id)
WITH opt AS (SELECT question_id, MIN(id) AS oid FROM question_options GROUP BY question_id)
SELECT q.id, o.oid, ss.id
FROM survey_sessions ss
JOIN users u ON u.id = ss.consumer_id AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
JOIN survey_questions q ON q.survey_id = ss.survey_id
JOIN opt o ON o.question_id = q.id
WHERE NOT EXISTS (SELECT 1 FROM survey_answers a WHERE a.session_id = ss.id);

INSERT IGNORE INTO survey_rewards (amount, credited_amount, issuance_settled, granted_at, processed_at, status, session_id)
SELECT 2500, 2500, 1, ss.completed_at, ss.completed_at, 'PROCESSED', ss.id
FROM survey_sessions ss
JOIN users u ON u.id = ss.consumer_id AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
WHERE ss.status = 'COMPLETED'
  AND NOT EXISTS (SELECT 1 FROM survey_rewards r WHERE r.session_id = ss.id);

-- ---------------------------------------------------------------------------
-- Movimientos de presupuesto del comercial (@lt_m_budget_transactions por billetera)
-- ---------------------------------------------------------------------------
INSERT INTO budget_transactions (amount_cents, created_at, description, reference_id, type, wallet_id)
WITH RECURSIVE seq (s) AS (SELECT 0 UNION ALL SELECT s + 1 FROM seq WHERE s < @lt_m_budget_transactions - 1),
comm AS (
    SELECT w.id AS wid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    JOIN wallets w ON w.commercial_id = u.id
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM budget_transactions b WHERE b.wallet_id = w.id)
)
SELECT 1000 + MOD(c.n * 31 + q.s * 97, 9000), TIMESTAMPADD(SECOND, -MOD(c.n * 7919 + q.s * 104729, 7776000), NOW()), NULL,
       CONCAT('lt-bt-', c.wid, '-', q.s),
       ELT(1 + MOD(q.s, 4), 'AD_VIEW', 'GAME_REWARD', 'BRANDING_REQUEST', 'MANUAL_ADJUSTMENT'), c.wid
FROM comm c
JOIN seq q;

-- ---------------------------------------------------------------------------
-- Contadores que dependen del historial sembrado
-- ---------------------------------------------------------------------------
UPDATE ads a
JOIN (SELECT l.ad_id, COUNT(*) AS likes FROM ad_likes l GROUP BY l.ad_id) x ON x.ad_id = a.id
SET a.current_likes = GREATEST(a.current_likes, x.likes)
WHERE a.title LIKE 'LT Ad %';

UPDATE campaigns cp
JOIN (SELECT g.campaign_id, COUNT(*) AS played, SUM(g.completed) AS done,
             COUNT(DISTINCT g.consumer_id) AS uniq, SUM(g.play_time_seconds) AS secs, SUM(g.coins_earned) AS spent
      FROM game_sessions g
      WHERE g.session_token LIKE 'lt-gs-%'
      GROUP BY g.campaign_id) x ON x.campaign_id = cp.id
SET cp.sessions_played = x.played, cp.completed_sessions = x.done, cp.unique_players_count = x.uniq,
    cp.total_play_time_seconds = x.secs, cp.spent_cents = x.spent
WHERE JSON_EXTRACT(cp.config_data, '$.loadtest') IS NOT NULL;

UPDATE raffles r
JOIN (SELECT t.raffle_id, COUNT(*) AS tickets, COUNT(DISTINCT t.ticket_owner_id) AS owners
      FROM raffle_tickets t
      WHERE t.ticket_number LIKE 'LT-%'
      GROUP BY t.raffle_id) x ON x.raffle_id = r.id
SET r.total_tickets_issued = x.tickets, r.total_participants = x.owners
WHERE r.title LIKE 'LT Raffle A%';
