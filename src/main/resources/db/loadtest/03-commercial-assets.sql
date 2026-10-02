-- ============================================================================
-- Sembrado de la prueba de carga · 03 · lo de los comerciales
--
-- Suscripción, inversiones, onboarding, contrato y documentos; anuncios con sus assets,
-- campañas, solicitudes de brandeo, encuestas, productos y stock. Variables: los multiplicadores
-- @lt_m_* de LoadTestSeedPlan. Montos en centavos. Idempotente por dueño o por llave natural
-- (título, referencia); el stock de productos queda con códigos RAW:, que LoadTestSeeder cifra
-- después con la llave de la app (no se puede en SQL).
--
-- Las audiencias son un POOL compartido de @lt_m_audience_pool filas, identificadas por
-- (min_age = 18, target_gender = ALL, max_age = 80 + i). target_audiences no tiene llave
-- natural, así que una audiencia por anuncio no se podría volver a enlazar al correr de nuevo.
-- Cada audiencia del pool lleva categorías (al menos una) y, algunas, municipios.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Pool de audiencias
-- ---------------------------------------------------------------------------
INSERT INTO target_audiences (min_age, max_age, target_gender)
WITH RECURSIVE seq (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @lt_m_audience_pool)
SELECT 18, 80 + s.i, 'ALL'
FROM seq s
WHERE NOT EXISTS (SELECT 1 FROM target_audiences t
                  WHERE t.min_age = 18 AND t.max_age = 80 + s.i AND t.target_gender = 'ALL');

-- Cada audiencia con 2 categorías distintas (>= 1: la colección lleva @Size(min = 1))
INSERT INTO target_audience_categories (target_audience_id, category_id)
WITH RECURSIVE seq (j) AS (SELECT 0 UNION ALL SELECT j + 1 FROM seq WHERE j < 1),
cat AS (SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn FROM categories)
SELECT ta.id, c.id
FROM target_audiences ta
JOIN seq s
JOIN cat c ON c.rn = MOD(ta.max_age + 3 * s.j, (SELECT COUNT(*) FROM cat))
WHERE ta.min_age = 18 AND ta.target_gender = 'ALL' AND ta.max_age BETWEEN 81 AND 80 + @lt_m_audience_pool
  AND NOT EXISTS (SELECT 1 FROM target_audience_categories x
                  WHERE x.target_audience_id = ta.id AND x.category_id = c.id);

-- Una de cada tres audiencias se limita a 2 ciudades; las demás quedan sin límite geográfico
INSERT INTO target_audience_municipalities (target_audience_id, municipality_code)
WITH RECURSIVE seq (j) AS (SELECT 0 UNION ALL SELECT j + 1 FROM seq WHERE j < 1),
city AS (
    SELECT mn.code, ROW_NUMBER() OVER (ORDER BY mn.code) - 1 AS rn
    FROM municipality mn
    WHERE mn.code IN ('05001', '08001', '11001', '13001', '17001', '54001', '63001', '66001', '68001', '76001')
)
SELECT ta.id, c.code
FROM target_audiences ta
JOIN seq s
JOIN city c ON c.rn = MOD(ta.max_age + 4 * s.j, (SELECT COUNT(*) FROM city))
WHERE ta.min_age = 18 AND ta.target_gender = 'ALL' AND ta.max_age BETWEEN 81 AND 80 + @lt_m_audience_pool
  AND MOD(ta.max_age, 3) = 0
  AND NOT EXISTS (SELECT 1 FROM target_audience_municipalities x
                  WHERE x.target_audience_id = ta.id AND x.municipality_code = c.code);

-- ---------------------------------------------------------------------------
-- Suscripción, inversiones, onboarding, contrato y documentos
-- ---------------------------------------------------------------------------
INSERT INTO subscriptions (id, amount_paid_cents, created_at, start_date, end_date, status,
                           wompi_reference, commercial_id, plan_id)
WITH comm AS (
    SELECT u.id AS uid, u.registered_date AS reg, cd.current_plan_id AS plan_id,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u JOIN commercial_details cd ON cd.user_id = u.id
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.commercial_id = u.id)
)
SELECT UUID_TO_BIN(UUID()), COALESCE(p.monthly_price_cents, 0), c.reg, c.reg,
       TIMESTAMPADD(DAY, 30, NOW()), 'ACTIVE', CONCAT('LT-SUB-', c.n), c.uid, c.plan_id
FROM comm c JOIN plans p ON p.id = c.plan_id;

-- Inversiones confirmadas (depósitos al presupuesto), 3 por comercial
INSERT INTO investments (confirmed, confirmed_at, created_at, deposit_amount_cents, wompi_reference,
                         plan_at_deposit_id, wallet_id)
WITH RECURSIVE seq (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_investments),
comm AS (
    SELECT w.id AS wallet_id, cd.current_plan_id AS plan_id, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    JOIN commercial_details cd ON cd.user_id = u.id
    JOIN wallets w ON w.commercial_id = u.id
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM investments i WHERE i.wallet_id = w.id)
)
SELECT 1, TIMESTAMPADD(DAY, 7 * s.k, c.reg), TIMESTAMPADD(DAY, 7 * s.k, c.reg),
       10000000 + 1000000 * s.k, CONCAT('LT-INV-', c.n, '-', s.k), c.plan_id, c.wallet_id
FROM comm c JOIN seq s;

INSERT INTO commercial_onboarding (commercial_details_id, current_step, created_at, completed_at,
                                   terms_version, terms_accepted_at, person_type, legal_rep_first_name,
                                   legal_rep_last_name, economic_activity_description,
                                   legal_identification_completed_at, diagnostic_completed_at, route,
                                   route_preliminary, verification_required, classified_at, route_confirmed,
                                   route_confirmed_at, selected_plan_id, requires_special_negotiation,
                                   min_investment_cents_snapshot, max_investment_cents_snapshot,
                                   investment_amount_cents_snapshot, sale_commission_pct_snapshot,
                                   max_keys_pct_snapshot, plan_accepted_at, documents_completed_at)
SELECT cd.user_id, 'COMPLETED', u.registered_date, u.registered_date,
       '1', u.registered_date, 'JURIDICA', 'Representante', 'Prueba', 'Actividad ficticia de la prueba de carga',
       u.registered_date, u.registered_date,
       ELT(1 + MOD(cd.user_id, 3), 'A', 'B', 'C'),
       0, 0, u.registered_date, 1,
       u.registered_date, p.id, 0,
       p.min_investment_cents, p.max_investment_cents,
       p.min_investment_cents, p.sale_commission_pct,
       p.max_keys_pct, u.registered_date, u.registered_date
FROM users u
JOIN commercial_details cd ON cd.user_id = u.id
JOIN plans p ON p.id = cd.current_plan_id
WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
ON DUPLICATE KEY UPDATE commercial_details_id = commercial_details_id;

INSERT IGNORE INTO commercial_documents (commercial_onboarding_id, document_type, object_key, original_file_name,
                                         size_bytes, mime_type, status, uploaded_at)
WITH docs AS (
    SELECT 1 AS k, 'RUT' AS document_type UNION ALL
    SELECT 2, 'CAMARA_COMERCIO' UNION ALL
    SELECT 3, 'CEDULA_REPRESENTANTE' UNION ALL
    SELECT 4, 'CERTIFICACION_BANCARIA'
)
SELECT co.id, d.document_type,
       CONCAT('legal/loadtest/', co.commercial_details_id, '/', LOWER(d.document_type), '.pdf'),
       CONCAT(LOWER(d.document_type), '.pdf'), 102400, 'APPLICATION_PDF', 'VALIDATED', co.created_at
FROM commercial_onboarding co
JOIN users u ON u.id = co.commercial_details_id
JOIN docs d ON d.k <= @lt_m_documents
WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid';

INSERT INTO commercial_contracts (commercial_id, commercial_onboarding_id, purpose, object_key, version, status,
                                  generated_at, business_approved_at)
SELECT co.commercial_details_id, co.id, 'ONBOARDING',
       CONCAT('legal/loadtest/', co.commercial_details_id, '/contrato-marco-v1.pdf'), 1, 'APPROVED',
       co.created_at, co.created_at
FROM commercial_onboarding co
JOIN users u ON u.id = co.commercial_details_id
WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
ON DUPLICATE KEY UPDATE commercial_onboarding_id = commercial_onboarding_id;

-- Solo los comerciales STANDARD tienen cuenta de prosperidad
INSERT INTO prosperity_accounts (commercial_id, balance_cents, accumulated_threshold_cents, total_absorbed_cents,
                                 total_reintegrated_cents, last_sequence, version, created_at, last_updated)
SELECT u.id, 0, 0, 0, 0, 0, 0, u.registered_date, NOW()
FROM users u
JOIN commercial_details cd ON cd.user_id = u.id
JOIN plans p ON p.id = cd.current_plan_id AND p.code = 'STANDARD'
WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
ON DUPLICATE KEY UPDATE commercial_id = commercial_id;

-- Solo los comerciales PREMIUM piden integración de catálogo de mascotas. Dos de cada tres ya están
-- APPROVED y asignadas a un diseñador (así el recorrido del diseñador puede guardar borradores, comentar y
-- publicar); la otra queda PENDING sin asignar.
INSERT INTO catalog_integration_requests (created_at, description, desired_effects, product_name, status,
                                          updated_at, commercial_id, budget_cents, spent_cents,
                                          assigned_designer_id)
WITH des AS (
    SELECT gd.user_id AS uid, ROW_NUMBER() OVER (ORDER BY gd.user_id) - 1 AS rn
    FROM game_designer_details gd JOIN users du ON du.id = gd.user_id
    WHERE du.email LIKE 'lt-designer-%@loadtest.invalid'
)
SELECT u.registered_date, 'Solicitud ficticia de la prueba de carga', 'Efecto ficticio',
       CONCAT('LT Producto mascota ', cd.user_id),
       IF(MOD(cd.user_id, 3) = 0, 'PENDING', 'APPROVED'),
       NOW(), cd.user_id, 0, 0,
       IF(MOD(cd.user_id, 3) = 0, NULL, d.uid)
FROM users u
JOIN commercial_details cd ON cd.user_id = u.id
JOIN plans p ON p.id = cd.current_plan_id AND p.code = 'PREMIUM'
LEFT JOIN des d ON d.rn = MOD(cd.user_id, (SELECT COUNT(*) FROM des))
WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM catalog_integration_requests r WHERE r.commercial_id = cd.user_id);

-- ---------------------------------------------------------------------------
-- Anuncios y sus assets (@lt_m_ads por comercial)
-- ---------------------------------------------------------------------------
INSERT INTO ads (created_at, current_likes, description, end_date, max_likes, max_likes_per_user_per_day,
                 reward_per_like, start_date, status, target_url, title, updated_at, version, commercial_id,
                 target_audience_id)
WITH RECURSIVE seq (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_ads),
pool AS (
    SELECT id, max_age - 80 AS pi FROM target_audiences
    WHERE min_age = 18 AND target_gender = 'ALL' AND max_age BETWEEN 81 AND 80 + @lt_m_audience_pool
),
comm AS (
    SELECT u.id AS uid, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
)
SELECT TIMESTAMPADD(DAY, 1, c.reg), MOD(c.n * 13 + s.k * 7, 500), 'Anuncio ficticio de la prueba de carga',
       TIMESTAMPADD(DAY, 60, NOW()), 100000, 5, 5000, TIMESTAMPADD(DAY, -30, NOW()),
       IF(s.k < @lt_m_ads, 'ACTIVE', 'PAUSED'), 'https://empresa.loadtest.invalid/landing',
       CONCAT('LT Ad ', c.n, '-', s.k), NOW(), 0, c.uid, p.id
FROM comm c
JOIN seq s
JOIN pool p ON p.pi = 1 + MOD(c.n + s.k, (SELECT COUNT(*) FROM pool))
WHERE NOT EXISTS (SELECT 1 FROM ads a WHERE a.commercial_id = c.uid AND a.title = CONCAT('LT Ad ', c.n, '-', s.k));

INSERT INTO ad_assets (duration_seconds, media_type, mime_type, object_key, size_bytes, status, uploaded_at,
                       ad_id, min_price_per_like_cents, version)
SELECT 15, 'VIDEO', 'VIDEO_MP4', CONCAT('ads/loadtest/', a.id, '.mp4'), 1048576, 'ATTACHED', a.created_at,
       a.id, 1000, 0
FROM ads a
WHERE a.title LIKE 'LT Ad %'
  AND NOT EXISTS (SELECT 1 FROM ad_assets x WHERE x.ad_id = a.id);

-- ---------------------------------------------------------------------------
-- Campañas (@lt_m_campaigns por comercial) y solicitudes de brandeo (@lt_m_branding_requests)
-- ---------------------------------------------------------------------------
INSERT INTO campaigns (average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
                       config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
                       score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
                       unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id,
                       target_audience_id)
WITH RECURSIVE seq (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_campaigns),
gc AS (
    SELECT gcd.id AS cdid, gcd.game_id, gcd.average_reward_per_session_cents AS avg_reward,
           gcd.completion_reward_cents AS completion, gcd.max_reward_per_session_cents AS max_reward,
           gcd.score_reward_factor AS factor,
           ROW_NUMBER() OVER (ORDER BY gcd.id) - 1 AS rn
    FROM game_config_definitions gcd
    JOIN games g ON g.id = gcd.game_id
    WHERE gcd.is_latest = 1 AND gcd.active = 1 AND g.active = 1
),
pool AS (
    SELECT id, max_age - 80 AS pi FROM target_audiences
    WHERE min_age = 18 AND target_gender = 'ALL' AND max_age BETWEEN 81 AND 80 + @lt_m_audience_pool
),
comm AS (
    SELECT u.id AS uid, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
)
SELECT g.avg_reward, 5000000, 0, g.completion,
       JSON_OBJECT('loadtest', TRUE, 'k', s.k), TIMESTAMPADD(DAY, 2, c.reg), TIMESTAMPADD(DAY, 60, NOW()),
       g.max_reward, 5, g.factor, 0, 0, TIMESTAMPADD(DAY, -30, NOW()), 'ACTIVE', 0, 0, NOW(), 0,
       c.uid, g.cdid, g.game_id, p.id
FROM comm c
JOIN seq s
JOIN gc g ON g.rn = MOD(c.n * 3 + s.k, (SELECT COUNT(*) FROM gc))
JOIN pool p ON p.pi = 1 + MOD(c.n + s.k * 2, (SELECT COUNT(*) FROM pool))
WHERE NOT EXISTS (SELECT 1 FROM campaigns x
                  WHERE x.commercial_id = c.uid AND x.config_data = JSON_OBJECT('loadtest', TRUE, 'k', s.k));

-- Brandeo: la primera solicitud espera revisión; la segunda ya la tiene un diseñador (las colas
-- de los 5 diseñadores crecen con los comerciales).
INSERT INTO branding_requests (brand_description, brand_name, budget_cents, campaign_goal, created_at, status,
                               updated_at, assigned_designer_id, commercial_id, game_id,
                               game_config_definition_id, max_sessions_per_user_per_day, target_audience_id,
                               start_date, end_date)
WITH RECURSIVE seq (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_branding_requests),
gc AS (
    SELECT gcd.id AS cdid, gcd.game_id, ROW_NUMBER() OVER (ORDER BY gcd.id) - 1 AS rn
    FROM game_config_definitions gcd
    JOIN games g ON g.id = gcd.game_id
    WHERE gcd.is_latest = 1 AND gcd.active = 1 AND g.active = 1
),
des AS (
    SELECT gd.user_id AS uid, ROW_NUMBER() OVER (ORDER BY gd.user_id) - 1 AS rn
    FROM game_designer_details gd JOIN users u ON u.id = gd.user_id
    WHERE u.email LIKE 'lt-designer-%@loadtest.invalid'
),
pool AS (
    SELECT id, max_age - 80 AS pi FROM target_audiences
    WHERE min_age = 18 AND target_gender = 'ALL' AND max_age BETWEEN 81 AND 80 + @lt_m_audience_pool
),
comm AS (
    SELECT u.id AS uid, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
)
SELECT 'Solicitud ficticia de brandeo de la prueba de carga', CONCAT('LT Brand ', c.n, '-', s.k), 3000000,
       ELT(1 + MOD(c.n + s.k, 4), 'APP_INSTALLS', 'BRAND_AWARENESS', 'PRODUCT_PROMOTION', 'WEBSITE_TRAFFIC'),
       TIMESTAMPADD(DAY, 3 + s.k, c.reg), IF(s.k = 1, 'PENDING_REVIEW', 'DESIGN_IN_PROGRESS'), NOW(),
       IF(s.k = 1, NULL, d.uid), c.uid, g.game_id, g.cdid, 3, p.id,
       TIMESTAMPADD(DAY, 10, NOW()), TIMESTAMPADD(DAY, 70, NOW())
FROM comm c
JOIN seq s
JOIN gc g ON g.rn = MOD(c.n * 5 + s.k, (SELECT COUNT(*) FROM gc))
JOIN des d ON d.rn = MOD(c.n, (SELECT COUNT(*) FROM des))
JOIN pool p ON p.pi = 1 + MOD(c.n + s.k * 3, (SELECT COUNT(*) FROM pool))
WHERE NOT EXISTS (SELECT 1 FROM branding_requests x
                  WHERE x.commercial_id = c.uid AND x.brand_name = CONCAT('LT Brand ', c.n, '-', s.k));

INSERT INTO branding_request_comments (author_name, author_role, author_user_id, content, created_at,
                                       related_status, branding_request_id)
WITH RECURSIVE seq (j) AS (SELECT 1 UNION ALL SELECT j + 1 FROM seq WHERE j < @lt_m_branding_comments)
SELECT IF(s.j = 1, 'Comercial Prueba', 'Diseñador Prueba'),
       IF(s.j = 1, 'COMMERCIAL', 'DESIGNER'),
       IF(s.j = 1, br.commercial_id, COALESCE(br.assigned_designer_id, br.commercial_id)),
       CONCAT('Comentario ficticio ', s.j), TIMESTAMPADD(HOUR, s.j, br.created_at), br.status, br.id
FROM branding_requests br
JOIN seq s
WHERE br.brand_name LIKE 'LT Brand %'
  AND NOT EXISTS (SELECT 1 FROM branding_request_comments x WHERE x.branding_request_id = br.id);

-- ---------------------------------------------------------------------------
-- Encuestas (@lt_m_surveys por comercial; 5 preguntas de 4 opciones cada una). El precio por pregunta es el
-- mínimo vigente (SURVEY_REWARD_PER_QUESTION_CENTS = 2.500 centavos): con menos, aumentar el cupo se rechaza.
-- ---------------------------------------------------------------------------
INSERT INTO surveys (created_at, description, ends_at, max_responses, response_count,
                     reward_amount_per_question_cents, starts_at, status, title, updated_at, creator_id,
                     target_audience_id)
WITH RECURSIVE seq (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_surveys),
pool AS (
    SELECT id, max_age - 80 AS pi FROM target_audiences
    WHERE min_age = 18 AND target_gender = 'ALL' AND max_age BETWEEN 81 AND 80 + @lt_m_audience_pool
),
comm AS (
    SELECT u.id AS uid, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
)
SELECT TIMESTAMPADD(DAY, 4, c.reg), 'Encuesta ficticia de la prueba de carga', TIMESTAMPADD(DAY, 60, NOW()),
       100000, 0, 2500, TIMESTAMPADD(DAY, -20, NOW()), 'ACTIVE', CONCAT('LT Survey ', c.n, '-', s.k), NOW(),
       c.uid, p.id
FROM comm c
JOIN seq s
JOIN pool p ON p.pi = 1 + MOD(c.n * 2 + s.k, (SELECT COUNT(*) FROM pool))
WHERE NOT EXISTS (SELECT 1 FROM surveys x WHERE x.creator_id = c.uid AND x.title = CONCAT('LT Survey ', c.n, '-', s.k));

INSERT INTO survey_questions (survey_id, `text`, `type`, order_index, is_required)
WITH RECURSIVE seq (q) AS (SELECT 1 UNION ALL SELECT q + 1 FROM seq WHERE q < @lt_m_questions_per_survey)
SELECT sv.id, CONCAT('Pregunta ficticia ', s.q), 'SINGLE_CHOICE', s.q - 1, 1
FROM surveys sv
JOIN seq s
WHERE sv.title LIKE 'LT Survey %'
  AND NOT EXISTS (SELECT 1 FROM survey_questions x WHERE x.survey_id = sv.id);

INSERT INTO question_options (order_index, `text`, question_id)
WITH RECURSIVE seq (o) AS (SELECT 1 UNION ALL SELECT o + 1 FROM seq WHERE o < @lt_m_options_per_question)
SELECT s.o - 1, CONCAT('Opción ficticia ', s.o), q.id
FROM survey_questions q
JOIN surveys sv ON sv.id = q.survey_id AND sv.title LIKE 'LT Survey %'
JOIN seq s
WHERE NOT EXISTS (SELECT 1 FROM question_options x WHERE x.question_id = q.id);

-- ---------------------------------------------------------------------------
-- Productos (@lt_m_products por comercial) y su stock (@lt_m_stock_per_product).
-- Precio fijo de 20.000 COP (2.000.000 centavos): las compras sembradas (06) suman exacto.
-- ---------------------------------------------------------------------------
INSERT INTO products (approved_at, average_rate, created_at, description, is_game_reward, max_keys_pct, name,
                      price_cents, review_count, status, updated_at, commercial_id, product_category_id,
                      product_type)
WITH RECURSIVE seq (k) AS (SELECT 1 UNION ALL SELECT k + 1 FROM seq WHERE k < @lt_m_products),
pcat AS (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) - 1 AS rn FROM product_category WHERE is_active = 1
),
comm AS (
    SELECT u.id AS uid, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
)
SELECT TIMESTAMPADD(DAY, 5, c.reg), NULL, TIMESTAMPADD(DAY, 4, c.reg), 'Producto digital ficticio', 0, 20,
       CONCAT('LT Product ', c.n, '-', s.k), 2000000, 0, 'ACTIVE', NOW(), c.uid, pc.id, 'DIGITAL'
FROM comm c
JOIN seq s
JOIN pcat pc ON pc.rn = MOD(c.n + s.k, (SELECT COUNT(*) FROM pcat))
WHERE NOT EXISTS (SELECT 1 FROM products x WHERE x.commercial_id = c.uid AND x.name = CONCAT('LT Product ', c.n, '-', s.k));

-- Stock con códigos RAW:LT-<producto>-<j>; code_hash lleva el mismo texto (único por producto)
-- hasta que LoadTestSeeder lo reemplaza por el cifrado y el HMAC reales.
INSERT INTO product_stock (code, code_hash, created_at, status, version, product_id)
WITH RECURSIVE seq (j) AS (SELECT 1 UNION ALL SELECT j + 1 FROM seq WHERE j < @lt_m_stock_per_product)
SELECT CONCAT('RAW:LT-', p.id, '-', s.j), CONCAT('RAW:LT-', p.id, '-', s.j), p.created_at, 'AVAILABLE', 0, p.id
FROM products p
JOIN seq s
WHERE p.name LIKE 'LT Product %'
  AND NOT EXISTS (SELECT 1 FROM product_stock x WHERE x.product_id = p.id);
