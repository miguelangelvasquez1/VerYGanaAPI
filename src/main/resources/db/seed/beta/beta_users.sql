-- ============================================================
-- SEED BETA/DEMO — USUARIOS BASE
-- ============================================================
-- Prerrequisito de todos los demás seeds de la demo (anuncios,
-- encuestas, juegos, sorteos, marketplace, mascotas, finanzas,
-- PQRS, etc.).
--
-- Este archivo SOLO crea usuarios y su perfil mínimo:
-- users, user_details, consumer_details/commercial_details/
-- admin_details/game_designer_details/
-- compliance_officer_details, consumer_preferences,
-- user_level_profile.
--
-- Deliberadamente NO crea key_wallets, wallets,
-- commercial_onboarding, commercial_documents ni
-- commercial_contracts — eso es alcance de las subtasks de
-- finanzas/onboarding, no de esta.
--
-- Password de TODOS los usuarios demo: "Test1234!".
-- Mismo mecanismo de hash (BCrypt, BCryptPasswordEncoder por
-- defecto) que usa el resto del proyecto — no se cambia el
-- mecanismo de autenticación — pero con un hash generado y
-- verificado propio.
--
-- El hash de db/seed/test/test-users.sql/test-level-users.sql
-- NO corresponde realmente a "Test1234!" pese a lo que dice
-- su comentario (se verificó con bcrypt.checkpw y falla), así
-- que no se reutilizó tal cual.
--
-- Credenciales documentadas en:
-- src/main/resources/db/seed/beta/CREDENTIALS.md
-- (única fuente).
--
-- Idempotencia: mismo patrón que el resto de seeds del proyecto —
-- INSERT ... ON DUPLICATE KEY UPDATE sobre columnas con UNIQUE KEY
-- (email, public_id, user_hash, designer_code, badge_number,
-- admin_code), más SELECT ... WHERE u.email = '...' para
-- encadenar los detalles al mismo user_id en cada corrida.
--
-- public_id / UUIDs FIJOS (no aleatorios), prefijo 0be7a000
-- exclusivo de este seed para no colisionar con
-- db/seed/test/test-users.sql:
--
-- 0be7a000-0001-... ADMIN
-- 0be7a000-0002-... GAME_DESIGNER
-- 0be7a000-0003-... COMPLIANCE_OFFICER
-- 0be7a000-0004-000...N COMMERCIAL
--   N=1 BASIC
--   N=2 STANDARD
--   N=3 PREMIUM
-- 0be7a000-0005-000...N CONSUMER
--   N=1..10
--
-- Teléfonos sintéticos 3900000001-3900000013 para el admin,
-- los comerciales y los 7 consumidores "pre-verificados".
-- Game designer, compliance officer y los 3 consumidores de
-- verificación SMS conservan números reales del equipo.
--
-- Los 3 consumidores de prueba de Twilio Verify (CONSUMER 1, 2 y 3)
-- usan números reales del equipo y quedan en account_status
-- PENDING_VERIFICATION a propósito: el objetivo de esas 3 cuentas
-- es que alguien complete la verificación SMS real durante la demo.
--
-- NO deben poder iniciar sesión hasta que eso ocurra
-- (ver CustomUserDetailsChecker + UserServiceImpl.verifyPhoneCode).
--
-- El COMPLIANCE_OFFICER también usa un número real del equipo
-- (no requiere verificación: ese flujo solo aplica a CONSUMER),
-- y queda ACTIVE.
--
-- Los otros 7 consumidores y el resto de roles quedan ACTIVE
-- directamente.
--
-- Este proyecto no tiene una columna/flag "phone_verified"
-- separada: el estado de verificación de teléfono ES el propio
-- account_status.
-- ============================================================


-- ============================================================
-- 1. ADMIN
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'veryganaoficial+admin@gmail.com',
           '3900000001',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'ADMIN',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'veryganaoficial+admin@gmail.com'
    ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO admin_details (
    user_id,
    admin_code
)
SELECT
    id,
    'ADMIN-BETA-01'
FROM users
WHERE email = 'veryganaoficial+admin@gmail.com'
    ON DUPLICATE KEY UPDATE admin_code = admin_code;


-- ============================================================
-- 2. GAME_DESIGNER
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'vgmiguel16+designer@gmail.com',
           '3001971366',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'GAME_DESIGNER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0002-0000-0000-000000000001')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'vgmiguel16+designer@gmail.com'
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
    'Miguel',
    'Vásquez',
    'GD-BETA-01',
    'Diseñador de juegos brandeados del equipo VerYGana para la demo beta.',
    0,
    true,
    CURDATE()
FROM users
WHERE email = 'vgmiguel16+designer@gmail.com'
    ON DUPLICATE KEY UPDATE designer_code = designer_code;


-- ============================================================
-- 3. COMPLIANCE_OFFICER
-- ============================================================
-- Nota: el rol real en el enum es COMPLIANCE_OFFICER
-- (no existe "OFFICIAL_COMPLIANCE" en
-- com.verygana2.models.enums.Role).

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'juanpablorodriguezglab+compliance@gmail.com',
           '3104206559',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'COMPLIANCE_OFFICER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0003-0000-0000-000000000001')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'juanpablorodriguezglab+compliance@gmail.com'
    ON DUPLICATE KEY UPDATE user_id = user_id;

INSERT INTO compliance_officer_details (
    user_id,
    name,
    last_name,
    badge_number
)
SELECT
    id,
    'Juan Pablo',
    'Rodríguez',
    'CO-BETA-01'
FROM users
WHERE email = 'juanpablorodriguezglab+compliance@gmail.com'
    ON DUPLICATE KEY UPDATE badge_number = badge_number;


-- ============================================================
-- 4. COMMERCIAL — plan BASIC
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'juanp.rodriguezg+commercial@uqvirtual.edu.co',
           '3900000004',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'COMMERCIAL',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0004-0000-0000-000000000001')
       )
    ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'juanp.rodriguezg+commercial@uqvirtual.edu.co'
    ON DUPLICATE KEY UPDATE user_id = user_id;

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
    'Panadería Trigo Dorado S.A.S',
    '900700101-5',
    '4711',
    'MC-BETA-0001',
    'CC',
    '1019000001',
    false,
    'LESS_THAN_500_SMMLV',
    '11001',
    'BOGOTÁ, D.C.',
    'BOGOTÁ, D.C.',
    (
        SELECT id
        FROM plans
        WHERE code = 'BASIC'
          AND active = true
        LIMIT 1
    )
FROM users u
WHERE u.email = 'juanp.rodriguezg+commercial@uqvirtual.edu.co'
ON DUPLICATE KEY UPDATE
                     company_name = VALUES(company_name),
                     ciiu_code = VALUES(ciiu_code),
                     mercantile_registration = VALUES(mercantile_registration),
                     annual_income_range = VALUES(annual_income_range),
                     municipality_code = VALUES(municipality_code),
                     municipality_name = VALUES(municipality_name),
                     department_name = VALUES(department_name),
                     current_plan_id = VALUES(current_plan_id);


-- ============================================================
-- 5. COMMERCIAL — plan STANDARD
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'comercial.standard@verygana.com',
           '3900000005',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'COMMERCIAL',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0004-0000-0000-000000000002')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'comercial.standard@verygana.com'
    ON DUPLICATE KEY UPDATE user_id = user_id;

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
    'Moda Urbana Colombia S.A.S',
    '900700202-6',
    '4771',
    'MC-BETA-0002',
    'CC',
    '1019000002',
    false,
    'FROM_500_TO_5000_SMMLV',
    '05001',
    'MEDELLÍN',
    'ANTIOQUIA',
    (
        SELECT id
        FROM plans
        WHERE code = 'STANDARD'
          AND active = true
        LIMIT 1
    )
FROM users u
WHERE u.email = 'comercial.standard@verygana.com'
ON DUPLICATE KEY UPDATE
                     company_name = VALUES(company_name),
                     ciiu_code = VALUES(ciiu_code),
                     mercantile_registration = VALUES(mercantile_registration),
                     annual_income_range = VALUES(annual_income_range),
                     municipality_code = VALUES(municipality_code),
                     municipality_name = VALUES(municipality_name),
                     department_name = VALUES(department_name),
                     current_plan_id = VALUES(current_plan_id);


-- ============================================================
-- 6. COMMERCIAL — plan PREMIUM
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'comercial.premium@verygana.com',
           '3900000006',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'COMMERCIAL',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0004-0000-0000-000000000003')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'comercial.premium@verygana.com'
    ON DUPLICATE KEY UPDATE user_id = user_id;

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
    'Ecosistema Andino S.A.S',
    '900700303-7',
    '7310',
    'MC-BETA-0003',
    'CC',
    '1019000003',
    false,
    'FROM_5000_TO_50000_SMMLV',
    '76001',
    'SANTIAGO DE CALI',
    'VALLE DEL CAUCA',
    (
        SELECT id
        FROM plans
        WHERE code = 'PREMIUM'
          AND active = true
        LIMIT 1
    )
FROM users u
WHERE u.email = 'comercial.premium@verygana.com'
ON DUPLICATE KEY UPDATE
                     company_name = VALUES(company_name),
                     ciiu_code = VALUES(ciiu_code),
                     mercantile_registration = VALUES(mercantile_registration),
                     annual_income_range = VALUES(annual_income_range),
                     municipality_code = VALUES(municipality_code),
                     municipality_name = VALUES(municipality_name),
                     department_name = VALUES(department_name),
                     current_plan_id = VALUES(current_plan_id);


-- ============================================================
-- 7. CONSUMER 1 — Twilio Verify (número real del equipo)
-- ============================================================
-- Queda en PENDING_VERIFICATION a propósito: falta completar
-- la verificación SMS real para pasar a ACTIVE.

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'juanp.mejiap1+consumer1@uqvirtual.edu.co',
           '3057472606',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'PENDING_VERIFICATION',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000001')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'juanp.mejiap1+consumer1@uqvirtual.edu.co'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000001',
    'juanpablo_mejia',
    'Juan Pablo',
    'Mejía',
    'BOGOTÁ, D.C.',
    'BOGOTÁ, D.C.',
    '11001',
    (SELECT id FROM avatars WHERE name = 'strawberry'),
    DATE_SUB(CURDATE(), INTERVAL 24 YEAR),
    'MALE',
    false,
    0,
    0,
    'REF-BETA-C01',
    'CC',
    '2010200001',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'juanp.mejiap1+consumer1@uqvirtual.edu.co'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    500,
    'BRONCE',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'juanp.mejiap1+consumer1@uqvirtual.edu.co'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'juanp.mejiap1+consumer1@uqvirtual.edu.co'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 8. CONSUMER 2 — Twilio Verify (número real del equipo)
-- ============================================================
-- Queda en PENDING_VERIFICATION a propósito
-- (ver nota de la sección 7).

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'helen8335+consumer2@gmail.com',
           '3219806868',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'PENDING_VERIFICATION',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000002')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'helen8335+consumer2@gmail.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000002',
    'helen_giraldo',
    'Helen',
    'Giraldo',
    'ANTIOQUIA',
    'MEDELLÍN',
    '05001',
    (SELECT id FROM avatars WHERE name = 'pineapple'),
    DATE_SUB(CURDATE(), INTERVAL 29 YEAR),
    'FEMALE',
    false,
    0,
    0,
    'REF-BETA-C02',
    'CC',
    '2010200002',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'helen8335+consumer2@gmail.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    2000,
    'PLATA',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'helen8335+consumer2@gmail.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'helen8335+consumer2@gmail.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 9. CONSUMER 3 — Twilio Verify (número real del equipo)
-- ============================================================
-- Queda en PENDING_VERIFICATION a propósito
-- (ver nota de la sección 7).

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'jnch2005+consumer3@gmail.com',
           '3135911252',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'PENDING_VERIFICATION',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000003')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'jnch2005+consumer3@gmail.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000003',
    'nicolas_castro',
    'Nicolás',
    'Castro',
    'VALLE DEL CAUCA',
    'SANTIAGO DE CALI',
    '76001',
    (SELECT id FROM avatars WHERE name = 'blueberry'),
    DATE_SUB(CURDATE(), INTERVAL 26 YEAR),
    'MALE',
    false,
    0,
    0,
    'REF-BETA-C03',
    'CC',
    '2010200003',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'jnch2005+consumer3@gmail.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    6000,
    'ORO',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'jnch2005+consumer3@gmail.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'jnch2005+consumer3@gmail.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 10. CONSUMER 4 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'juanparodriguezg+consumer4@gmail.com',
           '3900000007',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000004')
       )
    ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'juanparodriguezg+consumer4@gmail.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000004',
    'maria_torres',
    'María Fernanda',
    'Torres',
    'ATLÁNTICO',
    'BARRANQUILLA',
    '08001',
    (SELECT id FROM avatars WHERE name = 'strawberry'),
    DATE_SUB(CURDATE(), INTERVAL 34 YEAR),
    'FEMALE',
    false,
    0,
    0,
    'REF-BETA-C04',
    'CC',
    '2010200004',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'juanparodriguezg+consumer4@gmail.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    13000,
    'RUBI',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'juanparodriguezg+consumer4@gmail.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'juanparodriguezg+consumer4@gmail.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 11. CONSUMER 5 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'andres.felipe.gomez+consumer5@verygana.com',
           '3900000008',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000005')
       )
    ON DUPLICATE KEY UPDATE email = VALUES(email);

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'andres.felipe.gomez+consumer5@verygana.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000005',
    'andres_gomez',
    'Andrés Felipe',
    'Gómez',
    'BOLÍVAR',
    'CARTAGENA DE INDIAS',
    '13001',
    (SELECT id FROM avatars WHERE name = 'pineapple'),
    DATE_SUB(CURDATE(), INTERVAL 41 YEAR),
    'MALE',
    false,
    0,
    0,
    'REF-BETA-C05',
    'CC',
    '2010200005',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'andres.felipe.gomez+consumer5@verygana.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    26000,
    'ESMERALDA',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'andres.felipe.gomez+consumer5@verygana.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'andres.felipe.gomez+consumer5@verygana.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 12. CONSUMER 6 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'miguela.vasquezg1+consumer6@uqvirtual.edu.co',
           '3900000009',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000006')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'miguela.vasquezg1+consumer6@uqvirtual.edu.co'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000006',
    'camila_ruiz',
    'Camila Andrea',
    'Ruiz',
    'SANTANDER',
    'BUCARAMANGA',
    '68001',
    (SELECT id FROM avatars WHERE name = 'blueberry'),
    DATE_SUB(CURDATE(), INTERVAL 22 YEAR),
    'FEMALE',
    false,
    0,
    0,
    'REF-BETA-C06',
    'CC',
    '2010200006',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'miguela.vasquezg1+consumer6@uqvirtual.edu.co'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    40000,
    'DIAMANTE',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'miguela.vasquezg1+consumer6@uqvirtual.edu.co'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'miguela.vasquezg1+consumer6@uqvirtual.edu.co'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 13. CONSUMER 7 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'juanpablorodriguezglab+consumer7@gmail.com',
           '3900000010',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000007')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'juanpablorodriguezglab+consumer7@gmail.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000007',
    'santiago_herrera',
    'Santiago',
    'Herrera',
    'RISARALDA',
    'PEREIRA',
    '66001',
    (SELECT id FROM avatars WHERE name = 'strawberry'),
    DATE_SUB(CURDATE(), INTERVAL 55 YEAR),
    'MALE',
    false,
    0,
    0,
    'REF-BETA-C07',
    'CC',
    '2010200007',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'juanpablorodriguezglab+consumer7@gmail.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    500,
    'BRONCE',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'juanpablorodriguezglab+consumer7@gmail.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'juanpablorodriguezglab+consumer7@gmail.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 14. CONSUMER 8 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'valentina.ospina+consumer8@verygana.com',
           '3900000011',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000008')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'valentina.ospina+consumer8@verygana.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000008',
    'valentina_ospina',
    'Valentina',
    'Ospina',
    'CALDAS',
    'MANIZALES',
    '17001',
    (SELECT id FROM avatars WHERE name = 'pineapple'),
    DATE_SUB(CURDATE(), INTERVAL 19 YEAR),
    'OTHER',
    false,
    0,
    0,
    'REF-BETA-C08',
    'CC',
    '2010200008',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'valentina.ospina+consumer8@verygana.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    2000,
    'PLATA',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'valentina.ospina+consumer8@verygana.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'valentina.ospina+consumer8@verygana.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 15. CONSUMER 9 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'daniel.ricardo.pena+consumer9@verygana.com',
           '3900000012',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-000000000009')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'daniel.ricardo.pena+consumer9@verygana.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-000000000009',
    'daniel_pena',
    'Daniel Ricardo',
    'Peña',
    'NORTE DE SANTANDER',
    'SAN JOSÉ DE CÚCUTA',
    '54001',
    (SELECT id FROM avatars WHERE name = 'blueberry'),
    DATE_SUB(CURDATE(), INTERVAL 47 YEAR),
    'PREFER_NOT_TO_SAY',
    false,
    0,
    0,
    'REF-BETA-C09',
    'CC',
    '2010200009',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'daniel.ricardo.pena+consumer9@verygana.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    6000,
    'ORO',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'daniel.ricardo.pena+consumer9@verygana.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'daniel.ricardo.pena+consumer9@verygana.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);


-- ============================================================
-- 16. CONSUMER 10 — pre-verificado (ACTIVE desde el seed)
-- ============================================================

INSERT INTO users (
    email,
    phone_number,
    password,
    role,
    account_status,
    registered_date,
    public_id
)
VALUES (
           'laura.sofia.restrepo+consumer10@verygana.com',
           '3900000013',
           '$2a$10$7MfgytYKsUVBGdgYHm1zn.IBNWHDS4H1kZade9RdU83cZK82QHDJC',
           'CONSUMER',
           'ACTIVE',
           NOW(),
           UUID_TO_BIN('0be7a000-0005-0000-0000-00000000000a')
       )
    ON DUPLICATE KEY UPDATE email = email;

INSERT INTO user_details (user_id)
SELECT id
FROM users
WHERE email = 'laura.sofia.restrepo+consumer10@verygana.com'
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
    birth_date,
    gender,
    has_pet,
    ads_watched,
    daily_ad_count,
    referral_code,
    document_type,
    document_number,
    is_pep,
    terms_version,
    terms_accepted_at,
    age_declared_at
)
SELECT
    u.id,
    '0be7a000-0005-0000-0000-00000000000a',
    'laura_restrepo',
    'Laura Sofía',
    'Restrepo',
    'TOLIMA',
    'IBAGUÉ',
    '73001',
    (SELECT id FROM avatars WHERE name = 'strawberry'),
    DATE_SUB(CURDATE(), INTERVAL 63 YEAR),
    'FEMALE',
    false,
    0,
    0,
    'REF-BETA-C10',
    'CC',
    '2010200010',
    false,
    '1',
    NOW(),
    NOW()
FROM users u
WHERE u.email = 'laura.sofia.restrepo+consumer10@verygana.com'
    ON DUPLICATE KEY UPDATE user_name = user_name;

INSERT INTO user_level_profile (
    consumer_id,
    xp_total,
    current_level,
    benefits_paused,
    last_activity_at,
    reactivation_mission_active,
    created_at
)
SELECT
    cd.user_id,
    13000,
    'RUBI',
    false,
    NOW(),
    false,
    NOW()
FROM consumer_details cd
         JOIN users u ON u.id = cd.user_id
WHERE u.email = 'laura.sofia.restrepo+consumer10@verygana.com'
    ON DUPLICATE KEY UPDATE xp_total = xp_total;

INSERT INTO consumer_preferences (
    user_id,
    category_id
)
SELECT
    u.id,
    c.id
FROM users u
         JOIN categories c ON c.name = 'Tecnología'
WHERE u.email = 'laura.sofia.restrepo+consumer10@verygana.com'
  AND NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = u.id
      AND cp.category_id = c.id
);