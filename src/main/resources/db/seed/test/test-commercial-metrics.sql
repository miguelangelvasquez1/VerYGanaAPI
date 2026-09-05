-- ============================================================
-- SEED: actividad para los paneles de métricas del comercial
--   (/commercials/report/{ads,surveys,games,page-visits})
--
-- Depende de: test-users.sql (comerciales + consumers) y
--             test-campaigns.sql (campañas 1..20 del comercial Estándar).
--
-- Idempotente: ids fijos + NOT EXISTS. Seguro en cada arranque (dev).
--
-- Reparte la actividad en los últimos ~27 días para que las series
-- diarias (*ByDay) y el rango por defecto (30 días) tengan datos.
--
--   Empresa Demo S.A.S       (STANDARD) → Anuncios + Encuestas + Juegos
--   Ecosistema Premium S.A.S (PREMIUM)  → + Remisión (visitas a página)
--
-- Se usa TIMESTAMPADD (no DATE_SUB/INTERVAL) porque lo entienden tanto
-- MySQL como H2 (MODE=MySQL), donde corre el test de este seed.
-- ============================================================

SET @std_id  = (SELECT cd.user_id FROM commercial_details cd
                JOIN users u ON u.id = cd.user_id
                WHERE u.email = 'comercial@verygana.com' LIMIT 1);

SET @prem_id = (SELECT cd.user_id FROM commercial_details cd
                JOIN users u ON u.id = cd.user_id
                WHERE u.email = 'comercial-premium@verygana.com' LIMIT 1);


-- ============================================================
-- 1. ANUNCIOS del comercial STANDARD (ids 9600..9611)
--    Mezcla de estados para poblar el resumen del panel.
-- ============================================================

INSERT INTO ads (id, version, title, description, reward_per_like, max_likes, current_likes,
                 max_likes_per_user_per_day, status, created_at, updated_at, start_date, end_date,
                 commercial_id, target_url, target_audience_id, rejection_reason)
SELECT d.id, 0, d.title,
       'Anuncio de prueba para el panel de métricas (comercial Estándar).',
       d.rpl, d.maxl, d.curl, NULL, d.status,
       TIMESTAMPADD(DAY, -d.age_days, NOW()), NOW(),
       TIMESTAMPADD(DAY, -d.age_days, NOW()),
       CASE WHEN d.status = 'COMPLETED' THEN TIMESTAMPADD(DAY, -2, NOW())
            ELSE TIMESTAMPADD(DAY, 30, NOW()) END,
       @std_id, 'https://empresademo.example/landing', NULL,
       CASE WHEN d.status = 'REJECTED' THEN 'Material no cumple lineamientos de marca (seed).' END
FROM (
    SELECT 9600 AS id, 'Métricas · Lanzamiento de App' AS title, 8000 AS rpl, 200 AS maxl, 130 AS curl, 'ACTIVE' AS status, 27 AS age_days UNION ALL
    SELECT 9601, 'Métricas · Promo Fin de Semana',    6000, 150, 95,  'ACTIVE',    25 UNION ALL
    SELECT 9602, 'Métricas · Nueva Colección',        7000, 180, 70,  'ACTIVE',    22 UNION ALL
    SELECT 9603, 'Métricas · Cashback Octubre',       5000, 120, 45,  'ACTIVE',    18 UNION ALL
    SELECT 9604, 'Métricas · Envío Gratis',           9000, 100, 30,  'ACTIVE',    12 UNION ALL
    SELECT 9605, 'Métricas · Referidos x2',           6500, 160, 18,  'ACTIVE',    6  UNION ALL
    SELECT 9606, 'Métricas · Teaser Campaña',         4000, 100, 60,  'PAUSED',    20 UNION ALL
    SELECT 9607, 'Métricas · Remarketing Carrito',    5500, 140, 40,  'PAUSED',    15 UNION ALL
    SELECT 9608, 'Métricas · Black Friday 2025',      10000, 300, 300, 'COMPLETED', 24 UNION ALL
    SELECT 9609, 'Métricas · Aniversario Marca',      8000, 200, 200, 'COMPLETED', 21 UNION ALL
    SELECT 9610, 'Métricas · Liquidación Invierno',   7000, 150, 150, 'COMPLETED', 17 UNION ALL
    SELECT 9611, 'Métricas · Creatividad Descartada', 6000, 120, 0,   'REJECTED',  19
) d
WHERE @std_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ads a WHERE a.id = d.id);

INSERT INTO ad_assets (id, object_key, size_bytes, media_type, mime_type, status, duration_seconds, ad_id, uploaded_at)
SELECT a.id, CONCAT('ads/test/metrics/video-', a.id, '.mp4'), 1024, 'VIDEO', 'VIDEO_MP4', 'ATTACHED', 6, a.id, NOW()
FROM ads a
WHERE a.id BETWEEN 9600 AND 9611
  AND NOT EXISTS (SELECT 1 FROM ad_assets aa WHERE aa.id = a.id);


-- ============================================================
-- 2. LIKES (interacciones) de anuncios — reparto por día
--    ad_likes PK = (consumer_user_id, ad_id): 1 like por consumer/anuncio.
--    STANDARD: anuncios 9600..9608 · PREMIUM: anuncios 9500..9509
-- ============================================================

INSERT INTO ad_likes (consumer_user_id, ad_id, reward_amount, created_at)
SELECT c.uid, a.id, a.reward_per_like,
       TIMESTAMPADD(DAY, -(1 + MOD(c.idx * 7 + a.rn * 3, 26)), NOW())
FROM (
    SELECT cd.user_id AS uid, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) c
CROSS JOIN (
    SELECT id, reward_per_like, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM ads WHERE id BETWEEN 9600 AND 9608
) a
WHERE @std_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ad_likes al WHERE al.consumer_user_id = c.uid AND al.ad_id = a.id);

INSERT INTO ad_likes (consumer_user_id, ad_id, reward_amount, created_at)
SELECT c.uid, a.id, a.reward_per_like,
       TIMESTAMPADD(DAY, -(1 + MOD(c.idx * 5 + a.rn * 2, 26)), NOW())
FROM (
    SELECT cd.user_id AS uid, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) c
CROSS JOIN (
    SELECT id, reward_per_like, ROW_NUMBER() OVER (ORDER BY id) AS rn
    FROM ads WHERE id BETWEEN 9500 AND 9509
) a
WHERE @prem_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM ad_likes al WHERE al.consumer_user_id = c.uid AND al.ad_id = a.id);

-- current_likes (lifetime) nunca por debajo de los likes reales sembrados.
UPDATE ads a
SET a.current_likes = GREATEST(a.current_likes,
        (SELECT COUNT(*) FROM ad_likes al WHERE al.ad_id = a.id))
WHERE (a.id BETWEEN 9600 AND 9611) OR (a.id BETWEEN 9500 AND 9509);


-- ============================================================
-- 3. ENCUESTAS (comercial STANDARD 9700..9705 · PREMIUM 9710..9714)
--    + 3 preguntas por encuesta.
-- ============================================================

INSERT INTO surveys (id, title, description, creator_id, reward_amount_per_question_cents,
                     max_responses, response_count, status, starts_at, ends_at,
                     target_audience_id, created_at, updated_at)
SELECT d.id, d.title, 'Encuesta de prueba para el panel de métricas.', d.creator,
       500, 100, d.resp, d.status,
       TIMESTAMPADD(DAY, -d.age_days, NOW()),
       CASE WHEN d.status = 'COMPLETED' THEN TIMESTAMPADD(DAY, -1, NOW())
            ELSE TIMESTAMPADD(DAY, 30, NOW()) END,
       NULL, TIMESTAMPADD(DAY, -d.age_days, NOW()), NOW()
FROM (
    SELECT 9700 AS id, 'Métricas · Satisfacción del cliente' AS title, @std_id AS creator, 42 AS resp, 'ACTIVE' AS status, 26 AS age_days UNION ALL
    SELECT 9701, 'Métricas · Preferencias de producto',    @std_id,  33,  'ACTIVE',    22 UNION ALL
    SELECT 9702, 'Métricas · Uso de la app',               @std_id,  21,  'ACTIVE',    14 UNION ALL
    SELECT 9703, 'Métricas · Feedback post-compra',        @std_id,  12,  'PAUSED',    18 UNION ALL
    SELECT 9704, 'Métricas · Encuesta cerrada',            @std_id,  100, 'COMPLETED', 25 UNION ALL
    SELECT 9705, 'Métricas · Borrador interno',            @std_id,  0,   'DRAFT',     4  UNION ALL
    SELECT 9710, 'Métricas Premium · NPS trimestral',      @prem_id, 55,  'ACTIVE',    24 UNION ALL
    SELECT 9711, 'Métricas Premium · Marca y recordación', @prem_id, 40,  'ACTIVE',    19 UNION ALL
    SELECT 9712, 'Métricas Premium · Hábitos de consumo',  @prem_id, 27,  'ACTIVE',    11 UNION ALL
    SELECT 9713, 'Métricas Premium · Piloto pausado',      @prem_id, 15,  'PAUSED',    16 UNION ALL
    SELECT 9714, 'Métricas Premium · Estudio cerrado',     @prem_id, 100, 'COMPLETED', 23
) d
WHERE d.creator IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM surveys s WHERE s.id = d.id);

INSERT INTO survey_questions (survey_id, `text`, `type`, order_index, is_required)
SELECT s.id, q.qtext, q.qtype, q.qorder, q.qreq
FROM surveys s
CROSS JOIN (
    SELECT '¿Qué tan satisfecho estás en general?' AS qtext, 'RATING' AS qtype, 0 AS qorder, true  AS qreq UNION ALL
    SELECT '¿Recomendarías nuestra marca?',                'YES_NO',         1,           true  UNION ALL
    SELECT '¿Qué podríamos mejorar?',                      'TEXT',           2,           false
) q
WHERE s.id BETWEEN 9700 AND 9714
  AND NOT EXISTS (SELECT 1 FROM survey_questions sq WHERE sq.survey_id = s.id AND sq.order_index = q.qorder);


-- ============================================================
-- 4. SESIONES DE ENCUESTA + RECOMPENSAS
--    STANDARD: encuestas 9700..9704 · 100 sesiones (ids 970000..970099)
--    PREMIUM : encuestas 9710..9714 ·  80 sesiones (ids 971000..971079)
--    Estado por MOD(n,10): 6 COMPLETED / 2 ABANDONED / 1 EXPIRED / 1 ACTIVE
-- ============================================================

INSERT INTO survey_sessions (id, version, survey_id, consumer_id, status, started_at, expires_at, completed_at)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 99)
SELECT 970000 + s.n, 0,
       9700 + MOD(s.n, 5),
       cons.uid,
       CASE WHEN MOD(s.n, 10) < 6 THEN 'COMPLETED'
            WHEN MOD(s.n, 10) < 8 THEN 'ABANDONED'
            WHEN MOD(s.n, 10) = 8 THEN 'EXPIRED'
            ELSE 'ACTIVE' END,
       TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW()),
       TIMESTAMPADD(MINUTE, 30, TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW())),
       CASE WHEN MOD(s.n, 10) < 6
            THEN TIMESTAMPADD(MINUTE, 5, TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW())) END
FROM seq s
JOIN (
    SELECT cd.user_id AS uid, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) cons ON cons.idx = MOD(FLOOR(s.n / 5), 6)
WHERE @std_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM survey_sessions ss WHERE ss.id = 970000 + s.n);

INSERT INTO survey_sessions (id, version, survey_id, consumer_id, status, started_at, expires_at, completed_at)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 79)
SELECT 971000 + s.n, 0,
       9710 + MOD(s.n, 5),
       cons.uid,
       CASE WHEN MOD(s.n, 10) < 6 THEN 'COMPLETED'
            WHEN MOD(s.n, 10) < 8 THEN 'ABANDONED'
            WHEN MOD(s.n, 10) = 8 THEN 'EXPIRED'
            ELSE 'ACTIVE' END,
       TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW()),
       TIMESTAMPADD(MINUTE, 30, TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW())),
       CASE WHEN MOD(s.n, 10) < 6
            THEN TIMESTAMPADD(MINUTE, 5, TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW())) END
FROM seq s
JOIN (
    SELECT cd.user_id AS uid, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) cons ON cons.idx = MOD(FLOOR(s.n / 5), 6)
WHERE @prem_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM survey_sessions ss WHERE ss.id = 971000 + s.n);

-- Una recompensa PROCESSED por cada sesión completada (para rewardPaidCents).
INSERT INTO survey_rewards (id, session_id, amount, status, granted_at, processed_at)
SELECT 900000 + (ss.id - 970000), ss.id, 1500, 'PROCESSED', ss.completed_at, ss.completed_at
FROM survey_sessions ss
WHERE ss.id BETWEEN 970000 AND 970099
  AND ss.status = 'COMPLETED' AND ss.completed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM survey_rewards r WHERE r.session_id = ss.id);

INSERT INTO survey_rewards (id, session_id, amount, status, granted_at, processed_at)
SELECT 900100 + (ss.id - 971000), ss.id, 1500, 'PROCESSED', ss.completed_at, ss.completed_at
FROM survey_sessions ss
WHERE ss.id BETWEEN 971000 AND 971079
  AND ss.status = 'COMPLETED' AND ss.completed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM survey_rewards r WHERE r.session_id = ss.id);


-- ============================================================
-- 5. CAMPAÑAS + PARTIDAS
--    STANDARD: usa las campañas 1..8 (test-campaigns.sql); marca la 19
--              PAUSED y la 20 COMPLETED para variar el resumen.
--    PREMIUM : 4 campañas nuevas (8001..8004) clonando juego/config de 1..4.
-- ============================================================

UPDATE campaigns SET status = 'PAUSED'    WHERE id = 19 AND commercial_id = @std_id;
UPDATE campaigns SET status = 'COMPLETED' WHERE id = 20 AND commercial_id = @std_id;

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_audience_id, status, created_at, updated_at, version,
    sessions_played, completed_sessions, total_play_time_seconds, unique_players_count)
SELECT 8000 + src.id, src.game_id, src.config_definition_id, src.config_data, @prem_id,
    src.score_reward_factor, src.average_reward_per_session_cents, src.completion_reward_cents, src.max_reward_per_session_cents,
    20000000, 0, 5,
    TIMESTAMPADD(DAY, -25, NOW()), TIMESTAMPADD(DAY, 20, NOW()), NULL,
    CASE WHEN src.id = 4 THEN 'PAUSED' ELSE 'ACTIVE' END,
    TIMESTAMPADD(DAY, -25, NOW()), NOW(), 0,
    0, 0, 0, 0
FROM campaigns src
WHERE src.id BETWEEN 1 AND 4 AND @prem_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM campaigns c WHERE c.id = 8000 + src.id);

-- Partidas del comercial STANDARD (campañas 1..8) — 150 filas, ids 990000..990149
INSERT INTO game_sessions (id, session_token, user_hash, consumer_id, game_id, campaign_id,
    start_time, end_time, coins_earned, play_time_seconds, device_platform, completed, reward_granted, score)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 149)
SELECT 990000 + s.n,
       CONCAT('seed-gs-', 990000 + s.n),
       cons.uhash, cons.uid, cmp.game_id, cmp.id,
       TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW()),
       CASE WHEN MOD(s.n, 3) <> 0
            THEN TIMESTAMPADD(SECOND, 60 + MOD(s.n, 180), TIMESTAMPADD(DAY, -(1 + MOD(s.n, 25)), NOW())) END,
       CASE WHEN MOD(s.n, 3) <> 0 THEN 3000 + MOD(s.n * 17, 2500) ELSE 0 END,
       60 + MOD(s.n * 13, 200),
       CASE MOD(s.n, 3) WHEN 0 THEN 'PC' WHEN 1 THEN 'MOBILE' ELSE 'TABLET' END,
       MOD(s.n, 3) <> 0,
       MOD(s.n, 3) <> 0,
       MOD(s.n * 37, 1000)
FROM seq s
JOIN (
    SELECT id, game_id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn
    FROM campaigns WHERE commercial_id = @std_id AND id BETWEEN 1 AND 8
) cmp ON cmp.rn = MOD(s.n, 8)
JOIN (
    SELECT cd.user_id AS uid, cd.user_hash AS uhash, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) cons ON cons.idx = MOD(FLOOR(s.n / 8), 6)
WHERE @std_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM game_sessions gs WHERE gs.id = 990000 + s.n);

-- Partidas del comercial PREMIUM (campañas 8001..8004) — 120 filas, ids 991000..991119
INSERT INTO game_sessions (id, session_token, user_hash, consumer_id, game_id, campaign_id,
    start_time, end_time, coins_earned, play_time_seconds, device_platform, completed, reward_granted, score)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 119)
SELECT 991000 + s.n,
       CONCAT('seed-gs-', 991000 + s.n),
       cons.uhash, cons.uid, cmp.game_id, cmp.id,
       TIMESTAMPADD(DAY, -(1 + MOD(s.n, 24)), NOW()),
       CASE WHEN MOD(s.n, 4) <> 0
            THEN TIMESTAMPADD(SECOND, 60 + MOD(s.n, 150), TIMESTAMPADD(DAY, -(1 + MOD(s.n, 24)), NOW())) END,
       CASE WHEN MOD(s.n, 4) <> 0 THEN 3500 + MOD(s.n * 19, 3000) ELSE 0 END,
       55 + MOD(s.n * 11, 220),
       CASE MOD(s.n, 3) WHEN 0 THEN 'PC' WHEN 1 THEN 'MOBILE' ELSE 'TABLET' END,
       MOD(s.n, 4) <> 0,
       MOD(s.n, 4) <> 0,
       MOD(s.n * 29, 1000)
FROM seq s
JOIN (
    SELECT id, game_id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn
    FROM campaigns WHERE commercial_id = @prem_id AND id BETWEEN 8001 AND 8004
) cmp ON cmp.rn = MOD(s.n, 4)
JOIN (
    SELECT cd.user_id AS uid, cd.user_hash AS uhash, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) cons ON cons.idx = MOD(FLOOR(s.n / 4), 6)
WHERE @prem_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM game_sessions gs WHERE gs.id = 991000 + s.n);

-- Contadores persistidos de la campaña (los usa el resumen "lifetime" del panel).
UPDATE campaigns c SET
    c.sessions_played         = (SELECT COUNT(*)                              FROM game_sessions gs WHERE gs.campaign_id = c.id),
    c.completed_sessions      = (SELECT COUNT(*)                              FROM game_sessions gs WHERE gs.campaign_id = c.id AND gs.completed = true),
    c.unique_players_count    = (SELECT COUNT(DISTINCT gs.consumer_id)        FROM game_sessions gs WHERE gs.campaign_id = c.id),
    c.total_play_time_seconds = (SELECT COALESCE(SUM(gs.play_time_seconds),0) FROM game_sessions gs WHERE gs.campaign_id = c.id),
    c.spent_cents             = (SELECT COALESCE(SUM(gs.coins_earned),0)      FROM game_sessions gs WHERE gs.campaign_id = c.id)
WHERE (c.commercial_id = @std_id  AND c.id BETWEEN 1 AND 8)
   OR (c.commercial_id = @prem_id AND c.id BETWEEN 8001 AND 8004);


-- ============================================================
-- 6. VISITAS A LA PÁGINA OFICIAL — "Remisión" (solo PREMIUM)
--    200 filas, ids 960000..960199 · anuncios 9500..9509 como origen.
-- ============================================================

INSERT INTO commercial_page_visits (id, commercial_id, ad_id, consumer_id, target_url, source, user_hash, created_at)
WITH RECURSIVE seq (n) AS (SELECT 0 UNION ALL SELECT n + 1 FROM seq WHERE n < 199)
SELECT 960000 + s.n,
       @prem_id,
       9500 + MOD(s.n, 10),
       cons.uid,
       'https://ecosistemapremium.example/landing',
       'AD',
       cons.uhash,
       TIMESTAMPADD(MINUTE, MOD(s.n * 37, 1440), TIMESTAMPADD(DAY, -(1 + MOD(s.n, 27)), NOW()))
FROM seq s
JOIN (
    SELECT cd.user_id AS uid, cd.user_hash AS uhash, ROW_NUMBER() OVER (ORDER BY cd.user_id) - 1 AS idx
    FROM consumer_details cd JOIN users u ON u.id = cd.user_id
    WHERE u.email IN ('consumer@verygana.com','consumer1@verygana.com','consumer2@verygana.com',
                      'consumer3@verygana.com','consumer4@verygana.com','consumer5@verygana.com')
) cons ON cons.idx = MOD(s.n, 6)
WHERE @prem_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM commercial_page_visits v WHERE v.id = 960000 + s.n);
