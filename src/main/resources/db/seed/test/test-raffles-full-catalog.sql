-- ============================================================
-- SEED: Catálogo completo de rifas para pruebas de stress/manuales
-- Idempotente: se puede ejecutar múltiples veces sin duplicar.
--
-- Crea 16 rifas = 2 tipos (STANDARD/PREMIUM) x 8 estados
-- (DRAFT, ACTIVE, CLOSED, LIVE, DRAWING, COMPLETED, CANCELLED,
-- MISSED_DRAW), cada una con su premio (raffle_prizes), su imagen
-- (raffle_image_assets), la imagen de su premio (prize_image_assets)
-- y su regla de emisión (raffle_rules), para que /api/raffles/lives,
-- /actives, /{id} y /{id}/draw-status siempre tengan datos reales
-- que devolver.
--
-- IDs usados: 910-925 (raffles/raffle_prizes/raffle_image_assets/
-- raffle_rules, uno por rifa) y 910-911 (ticket_earning_rules
-- nuevas; la 900 REFERRAL ya existe en test-raffle-referral-rule.sql).
-- No colisiona con los IDs 900/901 ya usados por ese archivo.
--
-- prize_image_assets usa IDs 6-21 (uno por rifa, en el mismo orden):
-- los IDs 1-3 ya estaban ocupados por una rifa de 3 premios creada a
-- mano en el ambiente real, y 4-5 los usa test-raffle-referral-rule.sql
-- para los premios 900/901.
--
-- object_key sigue el mismo patrón que genera RaffleServiceImpl.
-- confirmRaffleCreation en la app real:
--   raffle_image_assets:  raffles/{raffleId}.png
--   prize_image_assets:   prizes/Rifa-{raffleId}/p{prizeId}.png
--
-- claim_code lleva el prefijo RAW: en todos los premios: en la app real
-- ese campo se guarda CIFRADO (ClaimCodeEncryptor, AES-256-GCM), algo que
-- un SQL crudo no puede generar (requiere IV aleatorio + la llave del
-- entorno). DataSeeder.seedPrizeClaimCodes() reemplaza el prefijo por el
-- valor cifrado real después de correr este script — igual que ya se hace
-- con product_stock.code. Sin esto, reclamar cualquiera de estos premios
-- desde el frontend falla con CodeEncryptionException al descifrar.
--
-- Fechas por estado — pensadas para que RaffleScheduler (corre cada
-- minuto, ver utils/cron/RaffleScheduler.java) NO mueva la rifa a
-- otro estado mientras la app está corriendo:
--   DRAFT       start_date en el FUTURO (si no, activateDraftRaffles la activa)
--   ACTIVE      end_date bien en el futuro (si no, closeActiveRaffles la cierra)
--   CLOSED      draw_date bien en el futuro (si no, se vuelve LIVE o MISSED_DRAW)
--   LIVE        draw_date bien en el futuro (si no, se dispara un sorteo real)
--   DRAWING     ningún query del scheduler filtra por DRAWING: estable tal cual
--   COMPLETED   ningún query del scheduler filtra por COMPLETED: estable tal cual
--   CANCELLED   ningún query del scheduler filtra por CANCELLED: estable tal cual
--   MISSED_DRAW ningún query del scheduler filtra por MISSED_DRAW: estable tal cual
-- ============================================================

-- ============================================================
-- FIX DE ESQUEMA: ddl-auto=update NO amplía un ENUM nativo de MySQL
-- ya existente cuando se agrega un valor nuevo al enum de Java. La
-- columna raffles.raffle_status quedó creada como
-- ENUM('ACTIVE','CANCELLED','CLOSED','COMPLETED','DRAFT','DRAWING','LIVE')
-- desde antes de agregar RaffleStatus.MISSED_DRAW, así que cualquier
-- INSERT/UPDATE con ese valor revienta con "Data truncated for column
-- 'raffle_status'" — no solo este seed, también RaffleScheduler.
-- handleMissedDraws() en producción. Este ALTER es idempotente (correr
-- de nuevo con la misma definición no hace nada), pero SOLO corre en
-- dev vía este seed: producción necesita este mismo ALTER como
-- migración manual antes de desplegar el cambio de MISSED_DRAW.
-- ============================================================
ALTER TABLE raffles MODIFY COLUMN raffle_status
    ENUM('DRAFT','ACTIVE','CLOSED','DRAWING','LIVE','COMPLETED','CANCELLED','MISSED_DRAW') NOT NULL;

SET @admin_id = (SELECT id FROM users WHERE email = 'admin@verygana.com' LIMIT 1);

-- ============================================================
-- 1. REGLAS GLOBALES ADICIONALES (ticket_earning_rules)
-- ============================================================
-- La 900 (REFERRAL) ya existe en test-raffle-referral-rule.sql.
-- Se agregan PURCHASE y DAILY_LOGIN para variar la fuente de cada
-- raffle_rules de este catálogo.

INSERT INTO ticket_earning_rules (
    id, rule_name, description, rule_type, is_active, priority,
    min_purchase_amount_cents, daily_login, referral_added_quantity,
    tickets_to_award, created_at, updated_at, created_by
)
SELECT
    910,
    'COMPRA - Compra minima (seed catalogo)',
    'Tickets por compra en marketplace, usado por el catalogo de rifas de prueba.',
    'PURCHASE',
    1, 50,
    50000,      -- min_purchase_amount_cents: $500 COP
    0, NULL,
    2,          -- tickets_to_award
    NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM ticket_earning_rules WHERE id = 910);

INSERT INTO ticket_earning_rules (
    id, rule_name, description, rule_type, is_active, priority,
    min_purchase_amount_cents, daily_login, referral_added_quantity,
    tickets_to_award, created_at, updated_at, created_by
)
SELECT
    911,
    'LOGIN DIARIO - Ticket diario (seed catalogo)',
    'Ticket por inicio de sesion diario, usado por el catalogo de rifas de prueba.',
    'DAILY_LOGIN',
    1, 10,
    NULL,
    1, NULL,
    1,          -- tickets_to_award
    NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM ticket_earning_rules WHERE id = 911);

-- ============================================================
-- 2. Procedimiento auxiliar: una rifa completa por bloque
-- ============================================================
-- No hay forma limpia de parametrizar INSERTs en SQL puro portable,
-- así que cada rifa repite el mismo patrón de 4 INSERTs (raffle,
-- raffle_rules, raffle_prizes, raffle_image_assets), cambiando solo
-- los valores. Todos son idempotentes por id.

-- ---------- 910: STANDARD / DRAFT ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    910, 'Rifa Standard - Borrador (seed)',
    'Rifa STANDARD en estado DRAFT para pruebas de catalogo.',
    'STANDARD', 'DRAFT',
    DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY), DATE_ADD(NOW(), INTERVAL 41 DAY),
    20, 1000, 0, 0, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 910);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 910, 910, 900, 1, 300, 0, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 910);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 910, 910, 'Bono de 50.000 COP', 'Premio de prueba (seed).', 'VerYGana', 50000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-DRAFT-910', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 910);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 910, 'raffles/910.png', 1024, 'IMAGE_PNG', 'ATTACHED', 910, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 910);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 6, 'prizes/Rifa-910/p910.png', 1024, 'IMAGE_PNG', 'VALIDATED', 910, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 6);

-- ---------- 911: PREMIUM / DRAFT ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    911, 'Rifa Premium - Borrador (seed)',
    'Rifa PREMIUM en estado DRAFT para pruebas de catalogo.',
    'PREMIUM', 'DRAFT',
    DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 40 DAY), DATE_ADD(NOW(), INTERVAL 41 DAY),
    12, 500, 0, 0, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 911);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 911, 911, 910, 1, 150, 0, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 911);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 911, 911, 'Kit para mascota premium', 'Premio de prueba (seed).', 'VerYGana', 250000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-DRAFT-911', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 911);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 911, 'raffles/911.png', 1024, 'IMAGE_PNG', 'ATTACHED', 911, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 911);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 7, 'prizes/Rifa-911/p911.png', 1024, 'IMAGE_PNG', 'VALIDATED', 911, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 7);

-- ---------- 912: STANDARD / ACTIVE ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    912, 'Rifa Standard - Activa (seed)',
    'Rifa STANDARD en estado ACTIVE para pruebas de catalogo.',
    'STANDARD', 'ACTIVE',
    DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY), DATE_ADD(NOW(), INTERVAL 31 DAY),
    20, 1000, 15, 6, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 912);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 912, 912, 911, 1, 300, 15, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 912);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 912, 912, 'Bono de 100.000 COP', 'Premio de prueba (seed).', 'VerYGana', 100000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-ACTIVE-912', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 912);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 912, 'raffles/912.png', 1024, 'IMAGE_PNG', 'ATTACHED', 912, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 912);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 8, 'prizes/Rifa-912/p912.png', 1024, 'IMAGE_PNG', 'VALIDATED', 912, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 8);

-- ---------- 913: PREMIUM / ACTIVE ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    913, 'Rifa Premium - Activa (seed)',
    'Rifa PREMIUM en estado ACTIVE para pruebas de catalogo.',
    'PREMIUM', 'ACTIVE',
    DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY),
    12, 500, 8, 4, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 913);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 913, 913, 900, 1, 150, 8, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 913);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 913, 913, 'Smartphone gama alta', 'Premio de prueba (seed).', 'VerYGana', 3500000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-ACTIVE-913', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 913);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 913, 'raffles/913.png', 1024, 'IMAGE_PNG', 'ATTACHED', 913, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 913);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 9, 'prizes/Rifa-913/p913.png', 1024, 'IMAGE_PNG', 'VALIDATED', 913, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 9);

-- ---------- 914: STANDARD / CLOSED ----------
-- draw_date lejos en el futuro a propósito: evita que el scheduler la
-- pase a LIVE (ventana de 60 min) o a MISSED_DRAW (draw_date pasado).
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    914, 'Rifa Standard - Cerrada (seed)',
    'Rifa STANDARD en estado CLOSED para pruebas de catalogo.',
    'STANDARD', 'CLOSED',
    DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY),
    20, 500, 500, 120, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 914);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 914, 914, 911, 1, 150, 150, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 914);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 914, 914, 'Bono de 80.000 COP', 'Premio de prueba (seed).', 'VerYGana', 80000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-CLOSED-914', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 914);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 914, 'raffles/914.png', 1024, 'IMAGE_PNG', 'ATTACHED', 914, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 914);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 10, 'prizes/Rifa-914/p914.png', 1024, 'IMAGE_PNG', 'VALIDATED', 914, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 10);

-- ---------- 915: PREMIUM / CLOSED ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    915, 'Rifa Premium - Cerrada (seed)',
    'Rifa PREMIUM en estado CLOSED para pruebas de catalogo.',
    'PREMIUM', 'CLOSED',
    DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY),
    12, 300, 300, 90, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 915);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 915, 915, 900, 1, 90, 90, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 915);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 915, 915, 'Tablet gama media', 'Premio de prueba (seed).', 'VerYGana', 900000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-CLOSED-915', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 915);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 915, 'raffles/915.png', 1024, 'IMAGE_PNG', 'ATTACHED', 915, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 915);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 11, 'prizes/Rifa-915/p915.png', 1024, 'IMAGE_PNG', 'VALIDATED', 915, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 11);

-- ---------- 916: STANDARD / LIVE ----------
-- draw_date lejos en el futuro a propósito: evita que scheduleUpcomingDraws
-- la tome dentro de su ventana de 65s y dispare un sorteo real.
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    916, 'Rifa Standard - En vivo (seed)',
    'Rifa STANDARD en estado LIVE para pruebas de sala de espera/draw-status.',
    'STANDARD', 'LIVE',
    DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY),
    20, 800, 800, 200, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 916);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 916, 916, 910, 1, 240, 240, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 916);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 916, 916, 'Bono de 150.000 COP', 'Premio de prueba (seed).', 'VerYGana', 150000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-LIVE-916', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 916);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 916, 'raffles/916.png', 1024, 'IMAGE_PNG', 'ATTACHED', 916, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 916);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 12, 'prizes/Rifa-916/p916.png', 1024, 'IMAGE_PNG', 'VALIDATED', 916, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 12);

-- ---------- 917: PREMIUM / LIVE ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    917, 'Rifa Premium - En vivo (seed)',
    'Rifa PREMIUM en estado LIVE para pruebas de sala de espera/draw-status.',
    'PREMIUM', 'LIVE',
    DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 90 DAY),
    12, 400, 400, 150, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 917);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 917, 917, 900, 1, 120, 120, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 917);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 917, 917, 'Consola de videojuegos', 'Premio de prueba (seed).', 'VerYGana', 2200000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-LIVE-917', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 917);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 917, 'raffles/917.png', 1024, 'IMAGE_PNG', 'ATTACHED', 917, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 917);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 13, 'prizes/Rifa-917/p917.png', 1024, 'IMAGE_PNG', 'VALIDATED', 917, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 13);

-- ---------- 918: STANDARD / DRAWING ----------
-- Ningún query del scheduler filtra por DRAWING: queda estable tal cual.
-- draw_date reciente (hace 1 minuto) simula un sorteo "en curso".
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    918, 'Rifa Standard - Sorteando (seed)',
    'Rifa STANDARD en estado DRAWING para pruebas de catalogo.',
    'STANDARD', 'DRAWING',
    DATE_SUB(NOW(), INTERVAL 32 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 MINUTE),
    20, 600, 600, 180, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 918);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 918, 918, 911, 1, 180, 180, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 918);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 918, 918, 'Bono de 120.000 COP', 'Premio de prueba (seed).', 'VerYGana', 120000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-DRAWING-918', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 918);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 918, 'raffles/918.png', 1024, 'IMAGE_PNG', 'ATTACHED', 918, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 918);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 14, 'prizes/Rifa-918/p918.png', 1024, 'IMAGE_PNG', 'VALIDATED', 918, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 14);

-- ---------- 919: PREMIUM / DRAWING ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    919, 'Rifa Premium - Sorteando (seed)',
    'Rifa PREMIUM en estado DRAWING para pruebas de catalogo.',
    'PREMIUM', 'DRAWING',
    DATE_SUB(NOW(), INTERVAL 18 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 1 MINUTE),
    12, 350, 350, 130, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 919);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 919, 919, 900, 1, 105, 105, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 919);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 919, 919, 'Camara profesional', 'Premio de prueba (seed).', 'VerYGana', 1800000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-DRAWING-919', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 919);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 919, 'raffles/919.png', 1024, 'IMAGE_PNG', 'ATTACHED', 919, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 919);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 15, 'prizes/Rifa-919/p919.png', 1024, 'IMAGE_PNG', 'VALIDATED', 919, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 15);

-- ---------- 920: STANDARD / COMPLETED ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    920, 'Rifa Standard - Completada (seed)',
    'Rifa STANDARD en estado COMPLETED para pruebas de catalogo.',
    'STANDARD', 'COMPLETED',
    DATE_SUB(NOW(), INTERVAL 36 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY),
    20, 500, 500, 140, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 920);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 920, 920, 910, 1, 150, 150, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 920);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 920, 920, 'Bono de 90.000 COP', 'Premio de prueba (seed).', 'VerYGana', 90000.00, 'DIGITAL', 1, 1, 1, 'DELIVERED', 'RAW:TEST-STD-COMPLETED-920', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 920);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 920, 'raffles/920.png', 1024, 'IMAGE_PNG', 'ATTACHED', 920, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 920);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 16, 'prizes/Rifa-920/p920.png', 1024, 'IMAGE_PNG', 'VALIDATED', 920, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 16);

-- ---------- 921: PREMIUM / COMPLETED ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    921, 'Rifa Premium - Completada (seed)',
    'Rifa PREMIUM en estado COMPLETED para pruebas de catalogo.',
    'PREMIUM', 'COMPLETED',
    DATE_SUB(NOW(), INTERVAL 25 DAY), DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 6 DAY),
    12, 300, 300, 100, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 921);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 921, 921, 900, 1, 90, 90, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 921);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 921, 921, 'Laptop gama media', 'Premio de prueba (seed).', 'VerYGana', 2800000.00, 'PHYSICAL', 1, 1, 1, 'DELIVERED', 'RAW:TEST-PRM-COMPLETED-921', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 921);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 921, 'raffles/921.png', 1024, 'IMAGE_PNG', 'ATTACHED', 921, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 921);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 17, 'prizes/Rifa-921/p921.png', 1024, 'IMAGE_PNG', 'VALIDATED', 921, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 17);

-- ---------- 922: STANDARD / CANCELLED ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    922, 'Rifa Standard - Cancelada (seed)',
    'Rifa STANDARD en estado CANCELLED para pruebas de catalogo.',
    'STANDARD', 'CANCELLED',
    DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 11 DAY),
    20, 1000, 40, 15, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 922);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 922, 922, 911, 1, 300, 40, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 922);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 922, 922, 'Bono de 60.000 COP', 'Premio de prueba (seed).', 'VerYGana', 60000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-CANCELLED-922', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 922);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 922, 'raffles/922.png', 1024, 'IMAGE_PNG', 'ATTACHED', 922, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 922);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 18, 'prizes/Rifa-922/p922.png', 1024, 'IMAGE_PNG', 'VALIDATED', 922, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 18);

-- ---------- 923: PREMIUM / CANCELLED ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    923, 'Rifa Premium - Cancelada (seed)',
    'Rifa PREMIUM en estado CANCELLED para pruebas de catalogo.',
    'PREMIUM', 'CANCELLED',
    DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY), DATE_ADD(NOW(), INTERVAL 15 DAY),
    12, 500, 20, 10, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 923);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 923, 923, 900, 1, 150, 20, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 923);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 923, 923, 'Audifonos inalambricos', 'Premio de prueba (seed).', 'VerYGana', 450000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-CANCELLED-923', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 923);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 923, 'raffles/923.png', 1024, 'IMAGE_PNG', 'ATTACHED', 923, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 923);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 19, 'prizes/Rifa-923/p923.png', 1024, 'IMAGE_PNG', 'VALIDATED', 923, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 19);

-- ---------- 924: STANDARD / MISSED_DRAW ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    924, 'Rifa Standard - Sorteo perdido (seed)',
    'Rifa STANDARD en estado MISSED_DRAW para pruebas de catalogo (revision manual del admin).',
    'STANDARD', 'MISSED_DRAW',
    DATE_SUB(NOW(), INTERVAL 33 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
    20, 400, 380, 110, 0, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 924);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 924, 924, 911, 1, 120, 114, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 924);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 924, 924, 'Bono de 70.000 COP', 'Premio de prueba (seed).', 'VerYGana', 70000.00, 'DIGITAL', 1, 1, 0, 'PENDING', 'RAW:TEST-STD-MISSED-924', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 924);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 924, 'raffles/924.png', 1024, 'IMAGE_PNG', 'ATTACHED', 924, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 924);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 20, 'prizes/Rifa-924/p924.png', 1024, 'IMAGE_PNG', 'VALIDATED', 924, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 20);

-- ---------- 925: PREMIUM / MISSED_DRAW ----------
INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by, terms_and_conditions
)
SELECT
    925, 'Rifa Premium - Sorteo perdido (seed)',
    'Rifa PREMIUM en estado MISSED_DRAW para pruebas de catalogo (revision manual del admin).',
    'PREMIUM', 'MISSED_DRAW',
    DATE_SUB(NOW(), INTERVAL 20 DAY), DATE_SUB(NOW(), INTERVAL 3 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY),
    12, 250, 240, 85, 1, 'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id, 'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 925);

INSERT INTO raffle_rules (id, raffle_id, rule_id, is_active, max_tickets_by_source, current_tickets_by_source, created_at, updated_at, created_by)
SELECT 925, 925, 900, 1, 75, 72, NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 925);

INSERT INTO raffle_prizes (id, raffle_id, title, description, brand, value, prize_type, position, quantity, claimed_count, prize_status, claim_code, claim_instructions, created_at, updated_at)
SELECT 925, 925, 'Reloj inteligente', 'Premio de prueba (seed).', 'VerYGana', 650000.00, 'PHYSICAL', 1, 1, 0, 'PENDING', 'RAW:TEST-PRM-MISSED-925', 'Premio de prueba - solo entorno de desarrollo.', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 925);

INSERT INTO raffle_image_assets (id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at)
SELECT 925, 'raffles/925.png', 1024, 'IMAGE_PNG', 'ATTACHED', 925, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 925);

INSERT INTO prize_image_assets (id, object_key, size_bytes, mime_type, status, prize_id, uploaded_at)
SELECT 21, 'prizes/Rifa-925/p925.png', 1024, 'IMAGE_PNG', 'VALIDATED', 925, NOW()
WHERE NOT EXISTS (SELECT 1 FROM prize_image_assets WHERE id = 21);
