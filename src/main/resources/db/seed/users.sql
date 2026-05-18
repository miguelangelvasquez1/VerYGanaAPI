-- ============================================================
-- 1. ADMIN USER
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    user_state,
    registered_date
)
VALUES (
    'admin@verygana.com',
    '3001000001',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'ADMIN',
    'ACTIVE',
    NOW()
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
    registered_date
)
VALUES (
    'comercial@verygana.com',
    '3001000002',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'COMMERCIAL',
    'ACTIVE',
    NOW()
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
    nit
)
SELECT
    id,
    'Empresa Demo S.A.S',
    '900123456-1'
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
    registered_date
)
VALUES (
    'consumer@verygana.com',
    '3001000003',
    '$2a$10$e5w/jR0653YLZK8t9lQIhe1/yA9u5oqcvjmQQpV9zCGq27onNPzWu',
    'CONSUMER',
    'ACTIVE',
    NOW()
)
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'consumer@verygana.com'
ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO consumer_details (
    user_id,
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
    referral_code
)
SELECT
    u.id,
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
    'REF-TEST-0001'
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