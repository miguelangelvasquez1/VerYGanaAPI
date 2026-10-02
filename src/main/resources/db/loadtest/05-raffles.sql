-- ============================================================================
-- Sembrado de la prueba de carga · 05 · rifas (globales)
--
-- @lt_m_raffles_active rifas activas y @lt_m_raffles_completed finalizadas, con sus premios
-- (claim_code con placeholder RAW:, que LoadTestSeeder cifra con la llave de la app), el
-- resultado del sorteo de cada finalizada y los ganadores pendientes de reclamar: el 1 % de
-- los consumidores (los de n mod 100 = 1), uno por premio (raffle_winners.prize_id es único).
-- Cada finalizada tiene 2 premios, así que alcanzan para hasta 100 ganadores.
-- Las rifas son globales: no crecen con los usuarios, salvo los ganadores.
-- ============================================================================

-- Rifas activas (la mitad STANDARD y la mitad PREMIUM)
INSERT INTO raffles (created_at, created_by, description, draw_date, draw_method, end_date, max_tickets_per_user,
                     modified_by, raffle_status, raffle_type, start_date, terms_and_conditions,
                     title, total_participants, total_tickets_issued, updated_at)
WITH RECURSIVE seq (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @lt_m_raffles_active),
adm AS (SELECT MIN(u.id) AS uid FROM users u WHERE u.role = 'ADMIN' AND u.email LIKE 'lt-admin-%@loadtest.invalid')
SELECT TIMESTAMPADD(DAY, -10, NOW()), adm.uid, 'Rifa ficticia de la prueba de carga', TIMESTAMPADD(DAY, 30, NOW()),
       'SYSTEM_RANDOM', TIMESTAMPADD(DAY, 29, NOW()), 100, adm.uid, 'ACTIVE', IF(MOD(s.i, 2) = 0, 'PREMIUM', 'STANDARD'),
       TIMESTAMPADD(DAY, -10, NOW()), 'Términos ficticios', CONCAT('LT Raffle A', s.i), 0, 0, NOW()
FROM seq s
JOIN adm
WHERE adm.uid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM raffles r WHERE r.title = CONCAT('LT Raffle A', s.i));

-- Rifas finalizadas, repartidas en los últimos 90 días
INSERT INTO raffles (created_at, created_by, description, draw_date, draw_method, end_date, max_tickets_per_user,
                     modified_by, raffle_status, raffle_type, start_date, terms_and_conditions,
                     title, total_participants, total_tickets_issued, updated_at)
WITH RECURSIVE seq (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @lt_m_raffles_completed),
adm AS (SELECT MIN(u.id) AS uid FROM users u WHERE u.role = 'ADMIN' AND u.email LIKE 'lt-admin-%@loadtest.invalid')
SELECT TIMESTAMPADD(DAY, -(100 + s.i), NOW()), adm.uid, 'Rifa finalizada ficticia', TIMESTAMPADD(DAY, -(90 - s.i), NOW()),
       'SYSTEM_RANDOM', TIMESTAMPADD(DAY, -(91 - s.i), NOW()), 100, adm.uid, 'COMPLETED',
       IF(MOD(s.i, 2) = 0, 'PREMIUM', 'STANDARD'), TIMESTAMPADD(DAY, -(100 + s.i), NOW()), 'Términos ficticios',
       CONCAT('LT Raffle C', s.i), 0, 0, NOW()
FROM seq s
JOIN adm
WHERE adm.uid IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM raffles r WHERE r.title = CONCAT('LT Raffle C', s.i));

-- Imagen de cada rifa (el detalle de la rifa la exige: sin ella responde 400). Llave de objeto
-- ficticia; nadie la descarga.
INSERT INTO raffle_image_assets (mime_type, object_key, size_bytes, status, uploaded_at, raffle_id)
SELECT 'IMAGE_PNG', CONCAT('loadtest/raffles/', r.id, '.png'), 1024, 'ATTACHED', NOW(), r.id
FROM raffles r
WHERE r.title LIKE 'LT Raffle %'
ON DUPLICATE KEY UPDATE raffle_id = raffle_id;

-- Resultado del sorteo de cada finalizada. draw_proof es el JSON que lee DrawProofResponseDTO.
INSERT INTO raffle_results (draw_proof, drawn_at, external_reference, raffle_id)
SELECT JSON_OBJECT('raffleId', r.id, 'raffleTitle', r.title, 'configuredDrawMethod', 'SYSTEM_RANDOM',
                   'actualDrawMethod', 'SYSTEM_RANDOM', 'totalParticipants', 0, 'totalTickets', 0,
                   'ticketPoolHash', SHA2(CONCAT('lt-pool-', r.id), 256), 'numberOfWinners', 0,
                   'winners', JSON_ARRAY()),
       r.draw_date, CONCAT('lt-draw-', r.id), r.id
FROM raffles r
WHERE r.title LIKE 'LT Raffle C%'
ON DUPLICATE KEY UPDATE draw_proof = VALUES(draw_proof);

-- Premios: 1 por rifa activa y 2 por finalizada. Código de reclamo con placeholder RAW:.
-- value es decimal en pesos en la entidad (Prize.value); es el valor comercial del premio y no
-- mueve saldo de nadie.
INSERT INTO raffle_prizes (brand, claim_code, claim_instructions, claimed_count, created_at, description,
                           position, prize_status, prize_type, quantity, title, updated_at, value, raffle_id)
WITH RECURSIVE seq (p) AS (SELECT 1 UNION ALL SELECT p + 1 FROM seq WHERE p < 2)
SELECT 'Marca ficticia', CONCAT('RAW:LT-PRZ-', r.id, '-', s.p), 'Instrucciones ficticias de reclamo', 0, r.created_at,
       'Premio ficticio', s.p, 'PENDING', 'DIGITAL', 1,
       CONCAT('LT Prize ', SUBSTRING(r.title, 11), '-', s.p), NOW(), 50000.00, r.id
FROM raffles r
JOIN seq s ON s.p = 1 OR r.title LIKE 'LT Raffle C%'
WHERE r.title LIKE 'LT Raffle %'
  AND NOT EXISTS (SELECT 1 FROM raffle_prizes x WHERE x.raffle_id = r.id AND x.position = s.p);

-- Imagen de cada premio (el listado de premios ganados la exige: sin ella responde 500).
INSERT INTO prize_image_assets (mime_type, object_key, size_bytes, status, uploaded_at, prize_id)
SELECT 'IMAGE_PNG', CONCAT('loadtest/prizes/', pz.id, '.png'), 1024, 'ATTACHED', NOW(), pz.id
FROM raffle_prizes pz
JOIN raffles r ON r.id = pz.raffle_id
WHERE r.title LIKE 'LT Raffle %'
ON DUPLICATE KEY UPDATE prize_id = prize_id;

-- Ganadores pendientes de reclamar (1 % de los consumidores: n mod 100 = 1).
-- El ganador n toma el premio k = (n - 1) div 100: rifa finalizada k div 2 + 1, premio k mod 2 + 1.
-- Su boleto ganador se crea aparte (LTW-<n>) para que el recorrido de reclamo lo encuentre.
INSERT INTO raffle_tickets (is_winner, issued_at, source, source_id, status, ticket_number, raffle_id, ticket_owner_id)
WITH win AS (
    SELECT u.id AS uid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
)
SELECT 1, TIMESTAMPADD(DAY, -(100 + w.n DIV 100), NOW()), 'PURCHASE', w.uid, 'ACTIVE', CONCAT('LTW-', w.n), r.id, w.uid
FROM win w
JOIN raffles r ON r.title = CONCAT('LT Raffle C', ((w.n - 1) DIV 100) DIV 2 + 1)
WHERE MOD(w.n, 100) = 1
  AND ((w.n - 1) DIV 100) DIV 2 + 1 <= @lt_m_raffles_completed
  AND NOT EXISTS (SELECT 1 FROM raffle_tickets t WHERE t.raffle_id = r.id AND t.ticket_number = CONCAT('LTW-', w.n));

INSERT INTO raffle_winners (claim_deadline, created_at, prize_claimed, prize_id, raffle_result_id,
                            winner_consumer_id, winning_ticket_id)
WITH win AS (
    SELECT u.id AS uid,
           CAST(SUBSTRING_INDEX(SUBSTRING_INDEX(u.email, '-', -1), '@', 1) AS UNSIGNED) AS n
    FROM users u
    WHERE u.role = 'CONSUMER' AND u.email LIKE 'lt-consumer-%@loadtest.invalid'
)
SELECT TIMESTAMPADD(DAY, 30, NOW()), TIMESTAMPADD(DAY, -1, NOW()), 0, pz.id, rs.id, w.uid, t.id
FROM win w
JOIN raffles r ON r.title = CONCAT('LT Raffle C', ((w.n - 1) DIV 100) DIV 2 + 1)
JOIN raffle_results rs ON rs.raffle_id = r.id
JOIN raffle_prizes pz ON pz.raffle_id = r.id AND pz.position = MOD((w.n - 1) DIV 100, 2) + 1
JOIN raffle_tickets t ON t.raffle_id = r.id AND t.ticket_number = CONCAT('LTW-', w.n)
WHERE MOD(w.n, 100) = 1
  AND NOT EXISTS (SELECT 1 FROM raffle_winners x WHERE x.winning_ticket_id = t.id);

-- Notificaciones del compañero de mascotas (globales, sin dueño: son de difusión)
INSERT INTO pet_notifications (active, date, external_id, message, is_read, title)
WITH RECURSIVE seq (i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM seq WHERE i < @lt_m_pet_notifications)
SELECT 1, CURDATE(), CONCAT('lt-pet-', s.i), CONCAT('Notificación ficticia ', s.i), 0, CONCAT('LT Aviso ', s.i)
FROM seq s
WHERE NOT EXISTS (SELECT 1 FROM pet_notifications x WHERE x.external_id = CONCAT('lt-pet-', s.i));
