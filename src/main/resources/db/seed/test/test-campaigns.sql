-- ============================================================
-- 20 CAMPAÑAS - INSERT IDEMPOTENTE (Solo se ejecuta una vez)
-- ============================================================

SET @commercial_id = (SELECT user_id FROM commercial_details cd
                      JOIN users u ON u.id = cd.user_id
                      WHERE u.email = 'comercial@verygana.com' LIMIT 1);

-- ============================================================
-- TARGET AUDIENCES para las campañas (IDs 1–20)
-- ============================================================

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 1, 18, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 1);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 2, 18, 60, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 2);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 3, 20, 70, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 3);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 4, 20, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 4);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 5, 22, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 5);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 6, 19, 70, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 6);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 7, 20, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 7);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 8, 21, 68, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 8);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 9, 20, 70, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 9);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 10, 19, 60, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 10);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 11, 22, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 11);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 12, 20, 70, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 12);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 13, 21, 68, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 13);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 14, 20, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 14);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 15, 19, 70, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 15);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 16, 20, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 16);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 17, 18, 55, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 17);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 18, 20, 65, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 18);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 19, 21, 68, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 19);

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 20, 22, 70, 'ALL' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 20);

-- ============================================================
-- CAMPAÑAS 1 al 20
-- Nota: score_reward_factor/*_reward_cents reemplazan el antiguo
-- modelo de coin_value/*_coins; budget_cents/spent_cents reemplazan
-- budget/spent (BigDecimal). Todo en centavos, igual que BrandingRequest
-- y GameConfigDefinition.
-- ============================================================

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 1, 1, 1, JSON_OBJECT('type', 'video_view', 'duration', 30), @commercial_id,
    50.0, 450000, 500000, 400000,
    25000000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'https://wa.me/573123456789?text=Tecnomecanica',
    1, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 1);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 2, 2, 1, JSON_OBJECT('type', 'video_view', 'duration', 25), @commercial_id,
    45.0, 360000, 405000, 315000,
    18000000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 25 DAY), 'https://wa.me/573123456789?text=Motos',
    2, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 2);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 3, 3, 1, JSON_OBJECT('type', 'video_view', 'duration', 20), @commercial_id,
    40.0, 290000, 320000, 260000,
    14000000, 0, 6,
    NOW(), DATE_ADD(NOW(), INTERVAL 20 DAY), 'https://wa.me/573123456789?text=Lavado',
    3, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 3);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 4, 4, 1, JSON_OBJECT('type', 'video_view', 'duration', 15), @commercial_id,
    35.0, 218750, 245000, 192500,
    9800000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 18 DAY), 'https://wa.me/573123456789?text=Alineacion',
    4, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 4);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 5, 5, 1, JSON_OBJECT('type', 'video_view', 'duration', 30), @commercial_id,
    55.0, 536250, 605000, 467500,
    30250000, 0, 4,
    NOW(), DATE_ADD(NOW(), INTERVAL 35 DAY), 'https://wa.me/573123456789?text=Repuestos',
    5, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 5);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 6, 6, 1, JSON_OBJECT('type', 'video_view', 'duration', 20), @commercial_id,
    38.0, 256500, 285000, 228000,
    12160000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 22 DAY), 'https://wa.me/573123456789?text=Aceite',
    6, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 6);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 7, 7, 1, JSON_OBJECT('type', 'video_view', 'duration', 25), @commercial_id,
    48.0, 408000, 456000, 360000,
    21600000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 28 DAY), 'https://wa.me/573123456789?text=Frenos',
    7, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 7);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 8, 8, 1, JSON_OBJECT('type', 'video_view', 'duration', 18), @commercial_id,
    42.0, 315000, 357000, 273000,
    15960000, 0, 6,
    NOW(), DATE_ADD(NOW(), INTERVAL 15 DAY), 'https://wa.me/573123456789?text=Diagnostico',
    8, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 8);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 9, 9, 1, JSON_OBJECT('type', 'video_view', 'duration', 28), @commercial_id,
    52.0, 481000, 546000, 416000,
    24960000, 0, 4,
    NOW(), DATE_ADD(NOW(), INTERVAL 32 DAY), 'https://wa.me/573123456789?text=Pintura',
    9, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 9);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 10, 10, 1, JSON_OBJECT('type', 'video_view', 'duration', 22), @commercial_id,
    47.0, 385400, 432400, 338400,
    19740000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 26 DAY), 'https://wa.me/573123456789?text=Accesorios',
    10, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 10);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 11, 11, 1, JSON_OBJECT('type', 'video_view', 'duration', 24), @commercial_id,
    50.0, 445000, 500000, 390000,
    23000000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'https://wa.me/573123456789?text=Diesel',
    11, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 11);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 12, 12, 1, JSON_OBJECT('type', 'video_view', 'duration', 20), @commercial_id,
    40.0, 280000, 320000, 240000,
    12000000, 0, 6,
    NOW(), DATE_ADD(NOW(), INTERVAL 20 DAY), 'https://wa.me/573123456789?text=RTM',
    12, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 12);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 13, 13, 1, JSON_OBJECT('type', 'video_view', 'duration', 27), @commercial_id,
    53.0, 503500, 572400, 434600,
    27030000, 0, 4,
    NOW(), DATE_ADD(NOW(), INTERVAL 33 DAY), 'https://wa.me/573123456789?text=Enderezado',
    13, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 13);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 14, 14, 1, JSON_OBJECT('type', 'video_view', 'duration', 19), @commercial_id,
    43.0, 331100, 369800, 292400,
    16770000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 24 DAY), 'https://wa.me/573123456789?text=Radiador',
    14, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 14);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 15, 15, 1, JSON_OBJECT('type', 'video_view', 'duration', 22), @commercial_id,
    46.0, 372600, 423200, 322000,
    18860000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 27 DAY), 'https://wa.me/573123456789?text=Bateria',
    15, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 15);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 16, 16, 1, JSON_OBJECT('type', 'video_view', 'duration', 18), @commercial_id,
    44.0, 341000, 387200, 294800,
    16280000, 0, 6,
    NOW(), DATE_ADD(NOW(), INTERVAL 21 DAY), 'https://wa.me/573123456789?text=Llantas',
    16, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 16);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 17, 17, 1, JSON_OBJECT('type', 'video_view', 'duration', 26), @commercial_id,
    51.0, 461550, 520200, 402900,
    24990000, 0, 4,
    NOW(), DATE_ADD(NOW(), INTERVAL 31 DAY), 'https://wa.me/573123456789?text=Tuning',
    17, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 17);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 18, 18, 1, JSON_OBJECT('type', 'video_view', 'duration', 23), @commercial_id,
    49.0, 423850, 480200, 367500,
    21070000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 29 DAY), 'https://wa.me/573123456789?text=Domicilio',
    18, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 18);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 19, 19, 1, JSON_OBJECT('type', 'video_view', 'duration', 21), @commercial_id,
    47.0, 392450, 441800, 343100,
    20680000, 0, 5,
    NOW(), DATE_ADD(NOW(), INTERVAL 23 DAY), 'https://wa.me/573123456789?text=Mantenimiento',
    19, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 19);

INSERT INTO campaigns (id, game_id, config_definition_id, config_data, commercial_id,
    score_reward_factor, average_reward_per_session_cents, completion_reward_cents, max_reward_per_session_cents,
    budget_cents, spent_cents, max_session_per_user_per_day,
    start_date, end_date, target_url,
    target_audience_id, status, created_at, updated_at)
SELECT 20, 20, 1, JSON_OBJECT('type', 'video_view', 'duration', 30), @commercial_id,
    58.0, 609000, 696000, 522000,
    35960000, 0, 4,
    NOW(), DATE_ADD(NOW(), INTERVAL 40 DAY), 'https://wa.me/573123456789',
    20, 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM campaigns WHERE id = 20);
