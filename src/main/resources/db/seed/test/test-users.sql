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

-- ------------------------------------------------------------
-- COMMERCIAL_DETAILS — completar identificación jurídica (paso 3)
-- y asignar plan (para no depender del flujo de checkout Wompi)
-- ------------------------------------------------------------

INSERT INTO commercial_details (
    user_id,
    company_name,
    nit,
    ciiu_code,
    mercantile_registration,
    legal_rep_doc_type,
    legal_rep_doc_number,
    is_pep,
    annual_income_range,
    municipality_code,
    municipality_name,
    department_name,
    current_plan_id
)
SELECT
    u.id,
    'Empresa Demo S.A.S',
    '900123456-1',
    '6201',
    '12345-BOG',
    'CC',
    '12345678',
    false,
    'FROM_500_TO_5000_SMMLV',
    '11001',
    'BOGOTÁ, D.C.',
    'BOGOTÁ, D.C.',
    (SELECT id FROM plans WHERE code = 'STANDARD' AND active = true LIMIT 1)
FROM users u
WHERE u.email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE
    ciiu_code = VALUES(ciiu_code),
    mercantile_registration = VALUES(mercantile_registration),
    annual_income_range = VALUES(annual_income_range),
    municipality_code = VALUES(municipality_code),
    municipality_name = VALUES(municipality_name),
    department_name = VALUES(department_name),
    current_plan_id = VALUES(current_plan_id);

-- ------------------------------------------------------------
-- COMMERCIAL_ONBOARDING — onboarding extendido completo (pasos 2-8)
-- Ruta B (comisión estándar) -> plan STANDARD, mismo motor de reglas
-- que CommercialOnboardingServiceImpl.classify()/acceptPlan().
-- ------------------------------------------------------------

INSERT INTO commercial_onboarding (
    commercial_details_id,
    current_step,
    created_at,
    completed_at,
    -- Paso 2: Términos y Condiciones
    terms_version,
    terms_document_url,
    terms_published_date,
    terms_accepted_at,
    terms_accepted_ip,
    terms_accepted_user_agent,
    -- Paso 3: Identificación jurídica
    person_type,
    legal_rep_first_name,
    legal_rep_last_name,
    economic_activity_description,
    address,
    legal_identification_completed_at,
    -- Paso 4: Diagnóstico comercial
    primary_goal,
    wants_fixed_fee,
    requires_custom_games,
    regulated_sector,
    requires_special_negotiation,
    diagnostic_completed_at,
    -- Paso 5: Clasificación automática
    route,
    route_explanation,
    classified_at,
    route_confirmed,
    route_confirmed_at,
    -- Paso 6-7: Plan y resumen económico
    selected_plan_id,
    requires_advisor_contact,
    monthly_fee_cents_snapshot,
    min_investment_cents_snapshot,
    max_investment_cents_snapshot,
    investment_amount_cents_snapshot,
    contract_duration_months,
    sale_commission_pct_snapshot,
    max_keys_pct_snapshot,
    tax_note_snapshot,
    liquidation_conditions_snapshot,
    plan_accepted_at,
    -- Paso 8: Carga documental
    documents_completed_at
)
SELECT
    u.id,
    'COMPLETED',
    NOW(),
    NOW(),
    -- Términos
    ld.version,
    ld.document_url,
    ld.published_date,
    NOW(),
    '127.0.0.1',
    'Seed/DataSeeder',
    -- Identificación jurídica
    'JURIDICA',
    'Juan',
    'Pérez',
    'Desarrollo de software y servicios de tecnología',
    'Calle 100 # 20-30, Bogotá',
    NOW(),
    -- Diagnóstico
    'AMBAS',
    false,
    false,
    false,
    false,
    NOW(),
    -- Clasificación
    'B',
    'Ruta B: pagará comisión únicamente cuando exista una venta, bajo el modelo estándar de VERYGANA, sin requisitos técnicos ni regulatorios especiales.',
    NOW(),
    true,
    NOW(),
    -- Plan
    p.id,
    false,
    NULL,
    p.min_investment_cents,
    p.max_investment_cents,
    p.min_investment_cents,
    NULL, -- contract_duration_months: solo aplica a BASIC, este seed usa STANDARD
    p.sale_commission_pct,
    p.max_keys_pct,
    'Los valores anteriores no incluyen IVA (19%). El IVA aplicable se liquidará según la normativa vigente al momento de cada transacción.',
    'Los pagos se liquidan de forma mensual, dentro de los primeros 5 días hábiles del mes siguiente, previa deducción de la comisión de VERYGANA, a la cuenta bancaria certificada por el comerciante.',
    NOW(),
    -- Documentos
    NOW()
FROM users u
JOIN plans p ON p.code = 'STANDARD' AND p.active = true
JOIN legal_documents ld ON ld.type = 'BUSINESS_OWNER_TERMS_AND_CONDITIONS' AND ld.active = true
WHERE u.email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE
    current_step = VALUES(current_step),
    completed_at = VALUES(completed_at),
    terms_version = VALUES(terms_version),
    terms_document_url = VALUES(terms_document_url),
    terms_published_date = VALUES(terms_published_date),
    terms_accepted_at = VALUES(terms_accepted_at),
    person_type = VALUES(person_type),
    legal_rep_first_name = VALUES(legal_rep_first_name),
    legal_rep_last_name = VALUES(legal_rep_last_name),
    economic_activity_description = VALUES(economic_activity_description),
    address = VALUES(address),
    legal_identification_completed_at = VALUES(legal_identification_completed_at),
    primary_goal = VALUES(primary_goal),
    wants_fixed_fee = VALUES(wants_fixed_fee),
    requires_custom_games = VALUES(requires_custom_games),
    regulated_sector = VALUES(regulated_sector),
    requires_special_negotiation = VALUES(requires_special_negotiation),
    diagnostic_completed_at = VALUES(diagnostic_completed_at),
    route = VALUES(route),
    route_explanation = VALUES(route_explanation),
    classified_at = VALUES(classified_at),
    route_confirmed = VALUES(route_confirmed),
    route_confirmed_at = VALUES(route_confirmed_at),
    selected_plan_id = VALUES(selected_plan_id),
    requires_advisor_contact = VALUES(requires_advisor_contact),
    min_investment_cents_snapshot = VALUES(min_investment_cents_snapshot),
    max_investment_cents_snapshot = VALUES(max_investment_cents_snapshot),
    investment_amount_cents_snapshot = VALUES(investment_amount_cents_snapshot),
    contract_duration_months = VALUES(contract_duration_months),
    sale_commission_pct_snapshot = VALUES(sale_commission_pct_snapshot),
    max_keys_pct_snapshot = VALUES(max_keys_pct_snapshot),
    tax_note_snapshot = VALUES(tax_note_snapshot),
    liquidation_conditions_snapshot = VALUES(liquidation_conditions_snapshot),
    plan_accepted_at = VALUES(plan_accepted_at),
    documents_completed_at = VALUES(documents_completed_at);

-- ------------------------------------------------------------
-- COMMERCIAL_DOCUMENTS — documentos requeridos ya validados (paso 8)
-- object_key fijo (no aleatorio) para que el seed sea idempotente.
-- ------------------------------------------------------------

INSERT INTO commercial_documents (
    commercial_onboarding_id, document_type, object_key, original_file_name,
    size_bytes, mime_type, status, uploaded_at
)
SELECT co.id, d.document_type, d.object_key, d.original_file_name, d.size_bytes, 'APPLICATION_PDF', 'VALIDATED', NOW()
FROM commercial_onboarding co
JOIN users u ON u.id = co.commercial_details_id
JOIN (
    SELECT 'RUT' AS document_type, 'legal/seed/commercial-demo/rut.pdf' AS object_key, 'rut.pdf' AS original_file_name, 102400 AS size_bytes
    UNION ALL
    SELECT 'CAMARA_COMERCIO', 'legal/seed/commercial-demo/camara-comercio.pdf', 'camara-comercio.pdf', 153600
    UNION ALL
    SELECT 'CEDULA_REPRESENTANTE', 'legal/seed/commercial-demo/cedula-representante.pdf', 'cedula-representante.pdf', 81920
    UNION ALL
    SELECT 'CERTIFICACION_BANCARIA', 'legal/seed/commercial-demo/certificacion-bancaria.pdf', 'certificacion-bancaria.pdf', 71680
) d
WHERE u.email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    mime_type = VALUES(mime_type),
    original_file_name = VALUES(original_file_name),
    size_bytes = VALUES(size_bytes);

-- ------------------------------------------------------------
-- COMMERCIAL_CONTRACTS — Contrato Marco ya aprobado por VERYGANA (pasos 9-11)
-- ------------------------------------------------------------

INSERT INTO commercial_contracts (
    commercial_onboarding_id, object_key, version, status, generated_at,
    business_approved_at, admin_reviewer_user_id, admin_reviewed_at, admin_decision_notes
)
SELECT
    co.id,
    'legal/seed/commercial-demo/contrato-marco-v1.pdf',
    1,
    'APPROVED',
    NOW(),
    NOW(),
    (SELECT id FROM users WHERE email = 'admin@verygana.com'),
    NOW(),
    'Aprobado (seed de datos de prueba)'
FROM commercial_onboarding co
JOIN users u ON u.id = co.commercial_details_id
WHERE u.email = 'comercial@verygana.com'
ON DUPLICATE KEY UPDATE
    status = VALUES(status),
    business_approved_at = VALUES(business_approved_at),
    admin_reviewer_user_id = VALUES(admin_reviewer_user_id),
    admin_reviewed_at = VALUES(admin_reviewed_at),
    admin_decision_notes = VALUES(admin_decision_notes);



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