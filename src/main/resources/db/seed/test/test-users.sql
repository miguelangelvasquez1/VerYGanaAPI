-- ============================================================
-- 1. ADMIN USER
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    user_state,
    registered_date,
    public_id
)
VALUES (
    'admin@verygana.com',
    '3001000001',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'ADMIN',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('f47ac10b-58cc-4372-a567-0e02b2c3d479')
)
ON DUPLICATE KEY UPDATE email = email;

-- ------------------------------------------------------------
-- USER_DETAILS BASE
-- ------------------------------------------------------------

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'admin@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- ------------------------------------------------------------
-- ADMIN_DETAILS
-- ------------------------------------------------------------

INSERT INTO admin_details (user_id, admin_code)
SELECT id, 'ADMIN-001'
FROM users
WHERE email = 'admin@verygana.com'
ON DUPLICATE KEY UPDATE admin_code = admin_code;



-- ============================================================
-- 2. COMMERCIAL USER
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    user_state,
    registered_date,
    public_id
)
VALUES (
    'comercial@verygana.com',
    '3001000002',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'COMMERCIAL',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('7c9e6679-7425-40de-944b-e07fc1f90ae7')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO commercial_details (
    user_id,
    company_name,
    nit,
    ciiu_code,
    legal_rep_doc_type,
    legal_rep_doc_number,
    is_pep
)
SELECT
    id,
    'Empresa Demo S.A.S',
    '900123456-1',
    '6201',
    'CC',
    '12345678',
    false
FROM users
WHERE email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE company_name = company_name;



-- ============================================================
-- 3. CONSUMER USER
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    user_state,
    registered_date,
    public_id
)

VALUES (
    'consumer@verygana.com',
    '3001000003',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'consumer@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id,
    user_hash,
    user_name,
    name,
    last_name,
    department_name,
    municipality_name,
    municipality_code,
    avatar_id,
    age,
    gender,
    has_pet,
    ads_watched,
    total_withdraws,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep
)
SELECT
    u.id,
    '550e8400-e29b-41d4-a716-446655440000',
    'consumer_test',
    'Usuario',
    'Prueba',
    'QUINDÍO',
    'ARMENIA',
    '63001',
    (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
    25,
    'MALE',
    false,
    0,
    0,
    0,
    'REF-TEST-0001',
    'CC',
    '12345678',
    false
FROM users u
WHERE u.email = 'consumer@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

-- ------------------------------------------------------------
-- CONSUMER PREFERENCES
-- ------------------------------------------------------------

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'consumer@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- ============================================================
-- 4. KEY_WALLET para CONSUMER
-- ============================================================

INSERT INTO key_wallets (
    id,
    consumer_id,
    purchase_keys_cents,
    blocked_purchase_keys_cents,
    connectivity_keys_cents,
    blocked_connectivity_keys_cents,
    created_at,
    updated_at
)
SELECT 
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440000'),
    cd.user_id,
    0,   -- purchase_keys_cents
    0,   -- blocked_purchase_keys_cents
    0,   -- connectivity_keys_cents
    0,    -- blocked_connectivity_keys_cents
    NOW(),
    NOW()

FROM consumer_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'consumer@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;


-- ============================================================
-- 5. WALLET para COMMERCIAL
-- ============================================================

INSERT INTO wallets (
    commercial_id,
    version,
    balance_cents,
    status,
    low_balance_threshold_pct,
    last_deposit_amount_cents,
    last_updated,
    created_at
)
SELECT 
    cd.user_id,
    1,
    5000000,                    -- 50.000 COP de saldo inicial (ajusta si quieres)
    'ACTIVE',                   -- WalletStatus.ACTIVE
    10,                         -- low_balance_threshold_pct (10%)
    5000000,                    -- last_deposit_amount_cents
    NOW(),                      -- last_updated
    NOW()                       -- created_at
FROM commercial_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE commercial_id = commercial_id;



-- ============================================================
-- 6. GAME DESIGNER USER
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    user_state,
    registered_date,
    public_id
)
VALUES (
    'designer@verygana.com',
    '3001000004',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'GAME_DESIGNER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('dddddddd-0000-0000-0000-000000000001')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'designer@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO game_designer_details (
    user_id,
    name,
    last_name,
    designer_code,
    bio,
    campaigns_designed,
    can_publish_directly,
    active,
    joined_at
)
SELECT
    id,
    'Game',
    'Designer',
    'GD-TEST-001',
    'Diseñador de prueba para el entorno de desarrollo',
    0,
    true,
    true,
    CURDATE()
FROM users
WHERE email = 'designer@verygana.com'
ON DUPLICATE KEY UPDATE designer_code = designer_code;



-- ============================================================
-- 7. CONSUMER TEST USERS (consumer1..consumer5)
-- ============================================================

-- ---- consumer1@verygana.com ----

INSERT INTO users (
    email, phone_number, password, role, user_state, registered_date, public_id
)
VALUES (
    'consumer1@verygana.com',
    '3001000005',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440001')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'consumer1@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name, department_name,
    municipality_name, municipality_code, avatar_id, age, gender, has_pet,
    ads_watched, total_withdraws, daily_ad_count, referral_code,
    document_type, document_number, is_pep
)
SELECT
    u.id, '550e8400-e29b-41d4-a716-446655440001', 'consumer_test1', 'Usuario', 'Prueba 1',
    'QUINDÍO', 'ARMENIA', '63001',
    (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
    25, 'MALE', false, 0, 0, 0, 'REF-TEST-0002', 'CC', '12345679', false
FROM users u
WHERE u.email = 'consumer1@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'consumer1@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO key_wallets (
    id, consumer_id, purchase_keys_cents, blocked_purchase_keys_cents,
    connectivity_keys_cents, blocked_connectivity_keys_cents, created_at, updated_at
)
SELECT
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440001'),
    cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'consumer1@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

-- ---- consumer2@verygana.com ----

INSERT INTO users (
    email, phone_number, password, role, user_state, registered_date, public_id
)
VALUES (
    'consumer2@verygana.com',
    '3001000006',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440002')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'consumer2@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name, department_name,
    municipality_name, municipality_code, avatar_id, age, gender, has_pet,
    ads_watched, total_withdraws, daily_ad_count, referral_code,
    document_type, document_number, is_pep
)
SELECT
    u.id, '550e8400-e29b-41d4-a716-446655440002', 'consumer_test2', 'Usuario', 'Prueba 2',
    'QUINDÍO', 'ARMENIA', '63001',
    (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
    25, 'MALE', false, 0, 0, 0, 'REF-TEST-0003', 'CC', '12345680', false
FROM users u
WHERE u.email = 'consumer2@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'consumer2@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO key_wallets (
    id, consumer_id, purchase_keys_cents, blocked_purchase_keys_cents,
    connectivity_keys_cents, blocked_connectivity_keys_cents, created_at, updated_at
)
SELECT
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440002'),
    cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'consumer2@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

-- ---- consumer3@verygana.com ----

INSERT INTO users (
    email, phone_number, password, role, user_state, registered_date, public_id
)
VALUES (
    'consumer3@verygana.com',
    '3001000007',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440003')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'consumer3@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name, department_name,
    municipality_name, municipality_code, avatar_id, age, gender, has_pet,
    ads_watched, total_withdraws, daily_ad_count, referral_code,
    document_type, document_number, is_pep
)
SELECT
    u.id, '550e8400-e29b-41d4-a716-446655440003', 'consumer_test3', 'Usuario', 'Prueba 3',
    'QUINDÍO', 'ARMENIA', '63001',
    (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
    25, 'MALE', false, 0, 0, 0, 'REF-TEST-0004', 'CC', '12345681', false
FROM users u
WHERE u.email = 'consumer3@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'consumer3@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO key_wallets (
    id, consumer_id, purchase_keys_cents, blocked_purchase_keys_cents,
    connectivity_keys_cents, blocked_connectivity_keys_cents, created_at, updated_at
)
SELECT
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440003'),
    cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'consumer3@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

-- ---- consumer4@verygana.com ----

INSERT INTO users (
    email, phone_number, password, role, user_state, registered_date, public_id
)
VALUES (
    'consumer4@verygana.com',
    '3001000008',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440004')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'consumer4@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name, department_name,
    municipality_name, municipality_code, avatar_id, age, gender, has_pet,
    ads_watched, total_withdraws, daily_ad_count, referral_code,
    document_type, document_number, is_pep
)
SELECT
    u.id, '550e8400-e29b-41d4-a716-446655440004', 'consumer_test4', 'Usuario', 'Prueba 4',
    'QUINDÍO', 'ARMENIA', '63001',
    (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
    25, 'MALE', false, 0, 0, 0, 'REF-TEST-0005', 'CC', '12345682', false
FROM users u
WHERE u.email = 'consumer4@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'consumer4@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO key_wallets (
    id, consumer_id, purchase_keys_cents, blocked_purchase_keys_cents,
    connectivity_keys_cents, blocked_connectivity_keys_cents, created_at, updated_at
)
SELECT
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440004'),
    cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'consumer4@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;

-- ---- consumer5@verygana.com ----

INSERT INTO users (
    email, phone_number, password, role, user_state, registered_date, public_id
)
VALUES (
    'consumer5@verygana.com',
    '3001000009',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW(),
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440005')
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id FROM users WHERE email = 'consumer5@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id, user_hash, user_name, name, last_name, department_name,
    municipality_name, municipality_code, avatar_id, age, gender, has_pet,
    ads_watched, total_withdraws, daily_ad_count, referral_code,
    document_type, document_number, is_pep
)
SELECT
    u.id, '550e8400-e29b-41d4-a716-446655440005', 'consumer_test5', 'Usuario', 'Prueba 5',
    'QUINDÍO', 'ARMENIA', '63001',
    (SELECT id FROM avatars ORDER BY sort_order ASC LIMIT 1),
    25, 'MALE', false, 0, 0, 0, 'REF-TEST-0006', 'CC', '12345683', false
FROM users u
WHERE u.email = 'consumer5@verygana.com'
ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO consumer_preferences (user_id, category_id)
SELECT u.id, c.id
FROM users u
JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'consumer5@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO key_wallets (
    id, consumer_id, purchase_keys_cents, blocked_purchase_keys_cents,
    connectivity_keys_cents, blocked_connectivity_keys_cents, created_at, updated_at
)
SELECT
    UUID_TO_BIN('550e8400-e29b-41d4-a716-446655440005'),
    cd.user_id, 0, 0, 0, 0, NOW(), NOW()
FROM consumer_details cd
JOIN users u ON u.id = cd.user_id
WHERE u.email = 'consumer5@verygana.com'
ON DUPLICATE KEY UPDATE consumer_id = consumer_id;