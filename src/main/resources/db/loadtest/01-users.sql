-- ============================================================================
-- Sembrado de la prueba de carga · 01 · usuarios y *_details
--
-- Lo corre LoadTestSeeder con estas variables de sesión ya fijadas:
--   @lt_consumers, @lt_commercials, @lt_designers, @lt_admins, @lt_compliance
--   @lt_password_hash  (BCrypt de LOADTEST_PASSWORD, calculado por la app)
--
-- Datos ficticios: correos lt-<rol>-<n>@loadtest.invalid (.invalid está reservado por el
-- RFC 2606 y no se puede entregar) y teléfonos 399xxxxxxx (rango no asignado en Colombia).
-- Idempotente: cada usuario se identifica por su correo (UNIQUE) y cada fila hija por su
-- usuario, así que correrlo de nuevo, o crecer de A a B, solo agrega lo que falta.
-- Sin procedimientos, funciones ni tablas temporales: el binlog con GTID de DO no los admite.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- users: un INSERT por rol. La secuencia 1..N sale de un CTE recursivo.
-- ---------------------------------------------------------------------------
INSERT INTO users (email, phone_number, password, role, account_status, registered_date, public_id,
                   password_configured, failed_login_attempts)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @lt_consumers)
SELECT CONCAT('lt-consumer-', n, '@loadtest.invalid'),
       CONCAT('399', LPAD(n, 7, '0')),
       @lt_password_hash, 'CONSUMER', 'ACTIVE',
       TIMESTAMPADD(DAY, -(1 + MOD(n * 37, 89)), NOW()),
       UUID_TO_BIN(UUID()), 1, 0
FROM seq
WHERE @lt_consumers >= 1
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO users (email, phone_number, password, role, account_status, registered_date, public_id,
                   password_configured, failed_login_attempts)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @lt_commercials)
SELECT CONCAT('lt-commercial-', n, '@loadtest.invalid'),
       CONCAT('3991', LPAD(n, 6, '0')),
       @lt_password_hash, 'COMMERCIAL', 'ACTIVE',
       TIMESTAMPADD(DAY, -(1 + MOD(n * 41, 89)), NOW()),
       UUID_TO_BIN(UUID()), 1, 0
FROM seq
WHERE @lt_commercials >= 1
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO users (email, phone_number, password, role, account_status, registered_date, public_id,
                   password_configured, failed_login_attempts)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @lt_designers)
SELECT CONCAT('lt-designer-', n, '@loadtest.invalid'),
       CONCAT('3992', LPAD(n, 6, '0')),
       @lt_password_hash, 'GAME_DESIGNER', 'ACTIVE', TIMESTAMPADD(DAY, -60, NOW()),
       UUID_TO_BIN(UUID()), 1, 0
FROM seq
WHERE @lt_designers >= 1
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO users (email, phone_number, password, role, account_status, registered_date, public_id,
                   password_configured, failed_login_attempts)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @lt_admins)
SELECT CONCAT('lt-admin-', n, '@loadtest.invalid'),
       CONCAT('3993', LPAD(n, 6, '0')),
       @lt_password_hash, 'ADMIN', 'ACTIVE', TIMESTAMPADD(DAY, -60, NOW()),
       UUID_TO_BIN(UUID()), 1, 0
FROM seq
WHERE @lt_admins >= 1
ON DUPLICATE KEY UPDATE email = email;

INSERT INTO users (email, phone_number, password, role, account_status, registered_date, public_id,
                   password_configured, failed_login_attempts)
WITH RECURSIVE seq (n) AS (SELECT 1 UNION ALL SELECT n + 1 FROM seq WHERE n < @lt_compliance)
SELECT CONCAT('lt-compliance-', n, '@loadtest.invalid'),
       CONCAT('3994', LPAD(n, 6, '0')),
       @lt_password_hash, 'COMPLIANCE_OFFICER', 'ACTIVE', TIMESTAMPADD(DAY, -60, NOW()),
       UUID_TO_BIN(UUID()), 1, 0
FROM seq
WHERE @lt_compliance >= 1
ON DUPLICATE KEY UPDATE email = email;

-- ---------------------------------------------------------------------------
-- user_details (base de la herencia JOINED: todos los *_details cuelgan de aquí)
-- ---------------------------------------------------------------------------
INSERT INTO user_details (user_id)
SELECT u.id
FROM users u
WHERE u.email LIKE 'lt-%@loadtest.invalid'
ON DUPLICATE KEY UPDATE user_id = user_id;

-- ---------------------------------------------------------------------------
-- consumer_details. Los consumidores se reparten entre 10 ciudades grandes y llevan
-- user_hash = su public_id, como los consumidores de db/seed/test.
-- ---------------------------------------------------------------------------
INSERT INTO consumer_details (user_id, user_hash, user_name, name, last_name, department_name,
                              municipality_name, municipality_code, avatar_id, birth_date,
                              gender, has_pet, ads_watched, daily_ad_count, referral_code,
                              document_type, document_number, is_pep, monthly_income_range, occupation,
                              terms_version, terms_accepted_at, age_declared_at)
WITH city AS (
    SELECT mn.code, mn.name AS mname, d.name AS dname,
           ROW_NUMBER() OVER (ORDER BY mn.code) - 1 AS rn
    FROM municipality mn
    JOIN department d ON d.code = mn.department_code
    WHERE mn.code IN ('05001', '08001', '11001', '13001', '17001', '54001', '63001', '66001', '68001', '76001')
),
cons AS (
    SELECT u.id AS uid, u.public_id AS pid, u.registered_date AS reg,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM consumer_details x WHERE x.user_id = u.id)
)
SELECT c.uid, BIN_TO_UUID(c.pid), CONCAT('ltc', c.n), 'Usuario', CONCAT('Prueba ', c.n),
       ct.dname, ct.mname, ct.code,
       (SELECT a.id FROM avatars a ORDER BY a.sort_order LIMIT 1),
       DATE_SUB(DATE_SUB(CURDATE(), INTERVAL (18 + MOD(c.n * 7, 40)) YEAR), INTERVAL MOD(c.n * 13, 300) DAY),
       ELT(1 + MOD(c.n, 3), 'MALE', 'FEMALE', 'OTHER'),
       1, 0, 0,
       CONCAT('LTC', LPAD(c.n, 7, '0')),
       'CC', CONCAT('9', LPAD(c.n, 9, '0')), 0,
       ELT(1 + MOD(c.n, 4), 'LESS_THAN_1_SMMLV', 'FROM_1_TO_3_SMMLV', 'FROM_3_TO_10_SMMLV', 'MORE_THAN_10_SMMLV'),
       'Estudiante',
       '1', c.reg, c.reg
FROM cons c
JOIN city ct ON ct.rn = MOD(c.n, (SELECT COUNT(*) FROM city));

-- ---------------------------------------------------------------------------
-- commercial_details. El plan sale de n mod 5 (0,1 BASIC · 2,3 STANDARD · 4 PREMIUM):
-- depende solo del número, no de rangos, para que al crecer de A a B nadie cambie de plan
-- (LoadTestSeedPlan.planOfCommercial).
-- ---------------------------------------------------------------------------
INSERT INTO commercial_details (user_id, company_name, nit, ciiu_code, mercantile_registration,
                                legal_rep_doc_type, legal_rep_doc_number, is_pep, annual_income_range,
                                municipality_code, municipality_name, department_name, current_plan_id,
                                commercial_activity_type)
WITH city AS (
    SELECT mn.code, mn.name AS mname, d.name AS dname,
           ROW_NUMBER() OVER (ORDER BY mn.code) - 1 AS rn
    FROM municipality mn
    JOIN department d ON d.code = mn.department_code
    WHERE mn.code IN ('05001', '08001', '11001', '13001', '17001', '54001', '63001', '66001', '68001', '76001')
),
comm AS (
    SELECT u.id AS uid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'COMMERCIAL' AND u.email LIKE 'lt-commercial-%@loadtest.invalid'
      AND NOT EXISTS (SELECT 1 FROM commercial_details x WHERE x.user_id = u.id)
)
SELECT c.uid, CONCAT('Empresa Prueba ', c.n), CONCAT('8', LPAD(c.n, 8, '0'), '-1'), '6201',
       CONCAT('LT-', LPAD(c.n, 6, '0')),
       'CC', CONCAT('8', LPAD(c.n, 9, '0')), 0, 'FROM_500_TO_5000_SMMLV',
       ct.code, ct.mname, ct.dname, p.id,
       IF(MOD(c.n, 2) = 0, 'PRODUCTS', 'SERVICES')
FROM comm c
JOIN city ct ON ct.rn = MOD(c.n, (SELECT COUNT(*) FROM city))
JOIN plans p ON p.code = ELT(1 + MOD(c.n, 5), 'BASIC', 'BASIC', 'STANDARD', 'STANDARD', 'PREMIUM')
            AND p.active = 1;

-- ---------------------------------------------------------------------------
-- Personal interno: fijo en A y en B. Los NOT EXISTS evitan duplicarlo al crecer.
-- ---------------------------------------------------------------------------
INSERT INTO game_designer_details (user_id, name, last_name, designer_code, bio, campaigns_designed, active, joined_at)
SELECT u.id, 'Diseñador', CONCAT('Prueba ', SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1)),
       CONCAT('LT-GD-', SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1)),
       'Diseñador ficticio de la prueba de carga', 0, 1, CURDATE()
FROM users u
WHERE u.role = 'GAME_DESIGNER' AND u.email LIKE 'lt-designer-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM game_designer_details x WHERE x.user_id = u.id);

INSERT INTO admin_details (user_id, admin_code)
SELECT u.id, CONCAT('LT-ADM-', SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1))
FROM users u
WHERE u.role = 'ADMIN' AND u.email LIKE 'lt-admin-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM admin_details x WHERE x.user_id = u.id);

INSERT INTO compliance_officer_details (user_id, name, last_name, badge_number)
SELECT u.id, 'Oficial', CONCAT('Prueba ', SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1)),
       CONCAT('LT-CO-', SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1))
FROM users u
WHERE u.role = 'COMPLIANCE_OFFICER' AND u.email LIKE 'lt-compliance-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM compliance_officer_details x WHERE x.user_id = u.id);
