-- ============================================================
-- TEST USERS — uno por cada nivel de gamificación
-- Password: Test1234! (mismo hash que test-users.sql)
-- ============================================================

-- ============================================================
-- 1. BRONCE  (XP: 500 — rango 0-999, mult: 0.5)
-- ============================================================
INSERT INTO users (email, phone_number, password, role, user_state, registered_date, public_id)
VALUES ('bronce@verygana.com', '3101000001',
        '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
        'CONSUMER', 'ACTIVE', NOW(), UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000001'))
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'bronce@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name,
    department_name, municipality_name, municipality_code,
    avatar_id, age, gender, has_pet, ads_watched, total_withdraws,
    daily_ad_count, referral_code, document_type, document_number, is_pep
)
SELECT u.id, 'aaaaaaaa-0000-0000-0000-000000000001', 'bronce_test',
       'Usuario', 'Bronce', 'QUINDÍO', 'ARMENIA', '63001',
       (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
       25, 'MALE', false, 0, 0, 0, 'REF-BRONCE-001', 'CC', '10000001', false
FROM users u WHERE u.email = 'bronce@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO key_wallets (id, consumer_id, purchase_keys, blocked_purchase_keys, connectivity_keys, blocked_connectivity_keys, created_at, updated_at)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000001'), cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'bronce@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO user_level_profile (consumer_id, xp_total, current_level, benefits_paused, last_activity_at, reactivation_mission_active, created_at)
SELECT cd.user_id, 500, 'BRONCE', false, NOW(), false, NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'bronce@verygana.com'
ON DUPLICATE KEY UPDATE xp_total = xp_total;


-- ============================================================
-- 2. PLATA  (XP: 2000 — rango 1000-3999, mult: 0.6)
-- ============================================================
INSERT INTO users (email, phone_number, password, role, user_state, registered_date, public_id)
VALUES ('plata@verygana.com', '3101000002',
        '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
        'CONSUMER', 'ACTIVE', NOW(), UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000002'))
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'plata@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name,
    department_name, municipality_name, municipality_code,
    avatar_id, age, gender, has_pet, ads_watched, total_withdraws,
    daily_ad_count, referral_code, document_type, document_number, is_pep
)
SELECT u.id, 'aaaaaaaa-0000-0000-0000-000000000002', 'plata_test',
       'Usuario', 'Plata', 'QUINDÍO', 'ARMENIA', '63001',
       (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
       28, 'FEMALE', false, 0, 0, 0, 'REF-PLATA-002', 'CC', '10000002', false
FROM users u WHERE u.email = 'plata@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO key_wallets (id, consumer_id, purchase_keys, blocked_purchase_keys, connectivity_keys, blocked_connectivity_keys, created_at, updated_at)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000002'), cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'plata@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO user_level_profile (consumer_id, xp_total, current_level, benefits_paused, last_activity_at, reactivation_mission_active, created_at)
SELECT cd.user_id, 2000, 'PLATA', false, NOW(), false, NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'plata@verygana.com'
ON DUPLICATE KEY UPDATE xp_total = xp_total;


-- ============================================================
-- 3. ORO  (XP: 6000 — rango 4000-8999, mult: 0.7)
-- ============================================================
INSERT INTO users (email, phone_number, password, role, user_state, registered_date, public_id)
VALUES ('oro@verygana.com', '3101000003',
        '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
        'CONSUMER', 'ACTIVE', NOW(), UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000003'))
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'oro@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name,
    department_name, municipality_name, municipality_code,
    avatar_id, age, gender, has_pet, ads_watched, total_withdraws,
    daily_ad_count, referral_code, document_type, document_number, is_pep
)
SELECT u.id, 'aaaaaaaa-0000-0000-0000-000000000003', 'oro_test',
       'Usuario', 'Oro', 'QUINDÍO', 'ARMENIA', '63001',
       (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
       30, 'MALE', false, 0, 0, 0, 'REF-ORO-003', 'CC', '10000003', false
FROM users u WHERE u.email = 'oro@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO key_wallets (id, consumer_id, purchase_keys, blocked_purchase_keys, connectivity_keys, blocked_connectivity_keys, created_at, updated_at)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000003'), cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'oro@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO user_level_profile (consumer_id, xp_total, current_level, benefits_paused, last_activity_at, reactivation_mission_active, created_at)
SELECT cd.user_id, 6000, 'ORO', false, NOW(), false, NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'oro@verygana.com'
ON DUPLICATE KEY UPDATE xp_total = xp_total;


-- ============================================================
-- 4. RUBI  (XP: 13000 — rango 9000-17999, mult: 0.8)
--    has_pet = true → acceso a rifas PREMIUM
-- ============================================================
INSERT INTO users (email, phone_number, password, role, user_state, registered_date, public_id)
VALUES ('rubi@verygana.com', '3101000004',
        '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
        'CONSUMER', 'ACTIVE', NOW(), UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000004'))
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'rubi@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name,
    department_name, municipality_name, municipality_code,
    avatar_id, age, gender, has_pet, ads_watched, total_withdraws,
    daily_ad_count, referral_code, document_type, document_number, is_pep
)
SELECT u.id, 'aaaaaaaa-0000-0000-0000-000000000004', 'rubi_test',
       'Usuario', 'Rubi', 'QUINDÍO', 'ARMENIA', '63001',
       (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
       32, 'FEMALE', true, 0, 0, 0, 'REF-RUBI-004', 'CC', '10000004', false
FROM users u WHERE u.email = 'rubi@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO key_wallets (id, consumer_id, purchase_keys, blocked_purchase_keys, connectivity_keys, blocked_connectivity_keys, created_at, updated_at)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000004'), cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'rubi@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO user_level_profile (consumer_id, xp_total, current_level, benefits_paused, last_activity_at, reactivation_mission_active, created_at)
SELECT cd.user_id, 13000, 'RUBI', false, NOW(), false, NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'rubi@verygana.com'
ON DUPLICATE KEY UPDATE xp_total = xp_total;


-- ============================================================
-- 5. ESMERALDA  (XP: 26000 — rango 18000-34999, mult: 0.9)
--    has_pet = true
-- ============================================================
INSERT INTO users (email, phone_number, password, role, user_state, registered_date, public_id)
VALUES ('esmeralda@verygana.com', '3101000005',
        '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
        'CONSUMER', 'ACTIVE', NOW(), UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000005'))
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'esmeralda@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name,
    department_name, municipality_name, municipality_code,
    avatar_id, age, gender, has_pet, ads_watched, total_withdraws,
    daily_ad_count, referral_code, document_type, document_number, is_pep
)
SELECT u.id, 'aaaaaaaa-0000-0000-0000-000000000005', 'esmeralda_test',
       'Usuario', 'Esmeralda', 'QUINDÍO', 'ARMENIA', '63001',
       (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
       35, 'MALE', true, 0, 0, 0, 'REF-ESMER-005', 'CC', '10000005', false
FROM users u WHERE u.email = 'esmeralda@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO key_wallets (id, consumer_id, purchase_keys, blocked_purchase_keys, connectivity_keys, blocked_connectivity_keys, created_at, updated_at)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000005'), cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'esmeralda@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO user_level_profile (consumer_id, xp_total, current_level, benefits_paused, last_activity_at, reactivation_mission_active, created_at)
SELECT cd.user_id, 26000, 'ESMERALDA', false, NOW(), false, NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'esmeralda@verygana.com'
ON DUPLICATE KEY UPDATE xp_total = xp_total;


-- ============================================================
-- 6. DIAMANTE  (XP: 40000 — rango 35000+, mult: 1.0)
--    has_pet = true
-- ============================================================
INSERT INTO users (email, phone_number, password, role, user_state, registered_date, public_id)
VALUES ('diamante@verygana.com', '3101000006',
        '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
        'CONSUMER', 'ACTIVE', NOW(), UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000006'))
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'diamante@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name,
    department_name, municipality_name, municipality_code,
    avatar_id, age, gender, has_pet, ads_watched, total_withdraws,
    daily_ad_count, referral_code, document_type, document_number, is_pep
)
SELECT u.id, 'aaaaaaaa-0000-0000-0000-000000000006', 'diamante_test',
       'Usuario', 'Diamante', 'QUINDÍO', 'ARMENIA', '63001',
       (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
       40, 'FEMALE', true, 0, 0, 0, 'REF-DIAM-006', 'CC', '10000006', false
FROM users u WHERE u.email = 'diamante@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO key_wallets (id, consumer_id, purchase_keys, blocked_purchase_keys, connectivity_keys, blocked_connectivity_keys, created_at, updated_at)
SELECT UUID_TO_BIN('aaaaaaaa-0000-0000-0000-000000000006'), cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'diamante@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

INSERT INTO user_level_profile (consumer_id, xp_total, current_level, benefits_paused, last_activity_at, reactivation_mission_active, created_at)
SELECT cd.user_id, 40000, 'DIAMANTE', false, NOW(), false, NOW()
FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'diamante@verygana.com'
ON DUPLICATE KEY UPDATE xp_total = xp_total;

-- ============================================================
-- CONSUMER PREFERENCES para los 6 usuarios de nivel
-- (requerido: la validación de ConsumerDetails exige >= 1 categoría
--  y se dispara al actualizar contadores, ej. al dar like a un ad)
-- ============================================================

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email IN (
    'bronce@verygana.com',
    'plata@verygana.com',
    'oro@verygana.com',
    'rubi@verygana.com',
    'esmeralda@verygana.com',
    'diamante@verygana.com'
)
ON DUPLICATE KEY UPDATE user_id = user_id;
