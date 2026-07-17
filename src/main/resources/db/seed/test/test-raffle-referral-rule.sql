-- ============================================================
-- SEED: Regla óptima de tickets por REFERIDO + rifa de prueba
-- Idempotente: se puede ejecutar múltiples veces sin duplicar.
--
-- Cómo la usa el backend (TicketDeliveryServiceImpl):
--   * La CANTIDAD de tickets NO sale de tickets_to_award, sino del
--     nivel del referidor (UserLevel.referralTickets: BRONCE=1 … DIAMANTE=6).
--   * La regla actúa como interruptor (is_active) y como tope de
--     presupuesto por fuente (max_tickets_by_source en raffle_rules).
--   * Si NO hay rifa activa con regla REFERRAL, el referidor solo
--     recibe XP (los referidos ya no dan llaves).
--   * En rifas PREMIUM solo participan RUBI o superior; por eso la
--     rifa de referidos óptima es STANDARD (todos los niveles ganan).
-- ============================================================

SET @admin_id = (SELECT id FROM users WHERE email = 'admin@verygana.com' LIMIT 1);

-- ============================================================
-- 1. REGLA GLOBAL DE REFERIDOS (ticket_earning_rules)
-- ============================================================
-- tickets_to_award = 1: es solo el mínimo que exige la entidad;
--   la cantidad real la decide el nivel del referidor.
-- priority = 100: máxima, para que gane sobre PURCHASE/DAILY_LOGIN
--   si algún flujo ordena por prioridad.
-- referral_added_quantity = NULL: el código actual no lo lee.

INSERT INTO ticket_earning_rules (
    id, rule_name, description, rule_type, is_active, priority,
    min_purchase_amount_cents, daily_login, referral_added_quantity,
    tickets_to_award, created_at, updated_at, created_by
)
SELECT
    900,
    'REFERRAL - Referido activado',
    'Tickets al referidor cuando su referido se registra y activa. Cantidad según nivel (BRONCE=1 ... DIAMANTE=6).',
    'REFERRAL',
    1,          -- is_active
    100,        -- priority máxima
    NULL,       -- min_purchase_amount_cents: no aplica a referidos
    0,          -- daily_login: no aplica
    NULL,       -- referral_added_quantity: no usado por el código
    1,          -- tickets_to_award: mínimo requerido; la cantidad real viene del nivel
    NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM ticket_earning_rules WHERE id = 900);

-- ============================================================
-- 2. RIFA STANDARD ACTIVA DE PRUEBA (raffles)
-- ============================================================
-- STANDARD: accesible para todos los niveles (PREMIUM excluye < RUBI).
-- max_tickets_per_user = 20: tope anti-abuso por usuario (sumando
--   todas las fuentes; un DIAMANTE necesitaría 4 referidos para llegar).
-- Ventana de 30 días con sorteo un día después del cierre.

INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by,
    terms_and_conditions
)
SELECT
    900,
    'Rifa de Referidos - Test',
    'Rifa STANDARD de prueba para validar la entrega de tickets por referidos.',
    'STANDARD',
    'ACTIVE',
    NOW(),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    DATE_ADD(NOW(), INTERVAL 31 DAY),
    20,         -- max_tickets_per_user
    1000,       -- max_total_tickets
    0, 0,
    0,          -- requires_pet
    'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id,
    'Rifa de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 900);

-- ============================================================
-- 3. VÍNCULO REGLA ↔ RIFA (raffle_rules)
-- ============================================================
-- max_tickets_by_source = 300 (30% del total de la rifa):
--   evita que los referidos acaparen la rifa frente a compras
--   y login diario, y acota el costo de una campaña de referidos.

INSERT INTO raffle_rules (
    id, raffle_id, rule_id, is_active,
    max_tickets_by_source, current_tickets_by_source,
    created_at, updated_at, created_by
)
SELECT
    900, 900, 900,
    1,          -- is_active
    300,        -- max_tickets_by_source: 30% de max_total_tickets
    0,
    NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 900);

-- ============================================================
-- 4. RIFA PREMIUM ACTIVA DE PRUEBA (raffles)
-- ============================================================
-- PREMIUM: solo RUBI, ESMERALDA y DIAMANTE pueden ganar tickets
-- de referido aquí (el flujo la salta para niveles menores y cae
-- en la STANDARD). Sirve para probar el filtro por nivel.
-- Sorteo ANTES que la STANDARD (día 15): como el flujo recorre las
-- rifas ordenadas por draw_date, un RUBI+ recibe sus tickets aquí
-- primero, lo que permite verificar la precedencia.

INSERT INTO raffles (
    id, title, description, raffle_type, raffle_status,
    start_date, end_date, draw_date,
    max_tickets_per_user, max_total_tickets,
    total_tickets_issued, total_participants,
    requires_pet, draw_method,
    created_at, updated_at, created_by,
    terms_and_conditions
)
SELECT
    901,
    'Rifa Premium de Referidos - Test',
    'Rifa PREMIUM de prueba: solo niveles RUBI o superior ganan tickets de referido.',
    'PREMIUM',
    'ACTIVE',
    NOW(),
    DATE_ADD(NOW(), INTERVAL 14 DAY),
    DATE_ADD(NOW(), INTERVAL 15 DAY),
    12,         -- max_tickets_per_user: 2 referidos de un DIAMANTE
    500,        -- max_total_tickets: premio mayor, cupo menor
    0, 0,
    0,          -- requires_pet
    'SYSTEM_RANDOM',
    NOW(), NOW(), @admin_id,
    'Rifa premium de prueba - solo entorno de desarrollo.'
WHERE NOT EXISTS (SELECT 1 FROM raffles WHERE id = 901);

INSERT INTO raffle_rules (
    id, raffle_id, rule_id, is_active,
    max_tickets_by_source, current_tickets_by_source,
    created_at, updated_at, created_by
)
SELECT
    901, 901, 900,
    1,          -- is_active
    150,        -- max_tickets_by_source: 30% de max_total_tickets
    0,
    NOW(), NOW(), @admin_id
WHERE NOT EXISTS (SELECT 1 FROM raffle_rules WHERE id = 901);

-- ============================================================
-- 5. PREMIOS (raffle_prizes)
-- ============================================================
-- REQUERIDO para que las rifas aparezcan en GET /api/raffles/actives:
-- la query del listado hace JOIN con premios e imagen; sin ellos la
-- rifa existe y entrega tickets, pero no se ve en la app.

INSERT INTO raffle_prizes (
    id, raffle_id, title, description, brand, value,
    prize_type, position, quantity, claimed_count,
    prize_status, claim_code, claim_instructions,
    created_at, updated_at
)
SELECT
    900, 900,
    'Bono de 100.000 COP',
    'Bono de compra para la rifa estándar de referidos (prueba).',
    'VerYGana', 100000.00,
    'DIGITAL', 1, 1, 0,
    'PENDING', 'TEST-STD-900',
    'Premio de prueba - solo entorno de desarrollo.',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 900);

INSERT INTO raffle_prizes (
    id, raffle_id, title, description, brand, value,
    prize_type, position, quantity, claimed_count,
    prize_status, claim_code, claim_instructions,
    created_at, updated_at
)
SELECT
    901, 901,
    'Smartphone gama alta',
    'Celular de gama alta para la rifa premium de referidos (prueba).',
    'VerYGana', 3500000.00,
    'PHYSICAL', 1, 1, 0,
    'PENDING', 'TEST-PRM-901',
    'Premio de prueba - solo entorno de desarrollo.',
    NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_prizes WHERE id = 901);

-- ============================================================
-- 6. IMÁGENES (raffle_image_assets)
-- ============================================================

INSERT INTO raffle_image_assets (
    id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at
)
SELECT
    900, 'raffles/test/rifa-referidos-standard.png', 1024,
    'IMAGE_PNG', 'ATTACHED', 900, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 900);

INSERT INTO raffle_image_assets (
    id, object_key, size_bytes, mime_type, status, raffle_id, uploaded_at
)
SELECT
    901, 'raffles/test/rifa-referidos-premium.png', 1024,
    'IMAGE_PNG', 'ATTACHED', 901, NOW()
WHERE NOT EXISTS (SELECT 1 FROM raffle_image_assets WHERE id = 901);
