-- ============================================================================
-- check-seed.sql · verificación del sembrado de la prueba de carga
--
-- Solo lectura. Se corre como verygana_monitor (SELECT sobre verygana.*):
--   $LT exec -T mysql mysql -u verygana_monitor -plt-monitor-local verygana < stress-tests/db/check-seed.sql
-- La salida es determinista: dos corridas del sembrado sobre la misma base tienen que dar
-- exactamente el mismo resultado, y los conteos por rol tienen que ser los del plan:
--   A (1.000): 940 / 50 / 5 / 3 / 2        B (10.000): 9.490 / 500 / 5 / 3 / 2
-- No imprime correos, teléfonos ni documentos: solo conteos. Solo cuenta los usuarios SEMBRADOS
-- (lt-<rol>-<n>@loadtest.invalid): los que crean los recorridos de registro (lt-reg-...) no la alteran.
-- ============================================================================

SET @lt_seeded_email = '^lt-(consumer|commercial|designer|admin|compliance)-[0-9]+@loadtest[.]invalid$';

SELECT '== 1. Usuarios sembrados por rol (esperado A: 940/50/5/3/2; B: 9490/500/5/3/2)' AS seccion;
SELECT u.role, COUNT(*) AS usuarios
FROM users u
WHERE u.email REGEXP @lt_seeded_email
GROUP BY u.role
ORDER BY FIELD(u.role, 'CONSUMER', 'COMMERCIAL', 'GAME_DESIGNER', 'ADMIN', 'COMPLIANCE_OFFICER');

SELECT 'total usuarios sembrados' AS dato, COUNT(*) AS valor FROM users WHERE email REGEXP @lt_seeded_email;

SELECT '== 2. Comerciales por plan (esperado 40/40/20 %: A 20/20/10; B 200/200/100)' AS seccion;
SELECT p.code AS plan, COUNT(*) AS comerciales
FROM users u
JOIN commercial_details cd ON cd.user_id = u.id
JOIN plans p ON p.id = cd.current_plan_id
WHERE u.email LIKE 'lt-commercial-%@loadtest.invalid'
GROUP BY p.code
ORDER BY FIELD(p.code, 'BASIC', 'STANDARD', 'PREMIUM');

SELECT '== 3. Filas por tabla (volumen histórico, proporcional entre A y B)' AS seccion;
SELECT 'consumer_details' AS tabla, COUNT(*) AS filas FROM consumer_details cd JOIN users u ON u.id = cd.user_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'commercial_details', COUNT(*) FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'consumer_preferences', COUNT(*) FROM consumer_preferences cp JOIN users u ON u.id = cp.user_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'key_wallets', COUNT(*) FROM key_wallets kw JOIN users u ON u.id = kw.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'user_level_profile', COUNT(*) FROM user_level_profile lp JOIN users u ON u.id = lp.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'key_transactions', COUNT(*) FROM key_transactions kt JOIN key_wallets kw ON kw.id = kt.key_wallet_id JOIN users u ON u.id = kw.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'xp_key_transaction_log', COUNT(*) FROM xp_key_transaction_log x JOIN users u ON u.id = x.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'ad_watch_session', COUNT(*) FROM ad_watch_session s JOIN users u ON u.id = s.consumer_user_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'ad_likes', COUNT(*) FROM ad_likes l JOIN users u ON u.id = l.consumer_user_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'game_sessions', COUNT(*) FROM game_sessions g WHERE g.session_token LIKE 'lt-gs-%'
UNION ALL SELECT 'game_session_metrics', COUNT(*) FROM game_session_metrics m JOIN game_sessions g ON g.id = m.session_id WHERE g.session_token LIKE 'lt-gs-%'
UNION ALL SELECT 'raffle_tickets', COUNT(*) FROM raffle_tickets t JOIN users u ON u.id = t.ticket_owner_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'raffle_participations', COUNT(*) FROM raffle_participations p JOIN users u ON u.id = p.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'notifications', COUNT(*) FROM notifications n JOIN users u ON u.id = n.user_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'pet_sessions', COUNT(*) FROM pet_sessions s JOIN users u ON u.id = s.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'pet_player_saves', COUNT(*) FROM pet_player_saves s JOIN users u ON u.id = s.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'purchases', COUNT(*) FROM purchases p WHERE p.reference_id LIKE 'LT-PUR-%'
UNION ALL SELECT 'purchase_items', COUNT(*) FROM purchase_items i JOIN purchases p ON p.id = i.purchase_id WHERE p.reference_id LIKE 'LT-PUR-%'
UNION ALL SELECT 'survey_sessions', COUNT(*) FROM survey_sessions s JOIN users u ON u.id = s.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'survey_answers', COUNT(*) FROM survey_answers a JOIN survey_sessions s ON s.id = a.session_id JOIN users u ON u.id = s.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'survey_rewards', COUNT(*) FROM survey_rewards r JOIN survey_sessions s ON s.id = r.session_id JOIN users u ON u.id = s.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'commercial_page_visits', COUNT(*) FROM commercial_page_visits v JOIN users u ON u.id = v.consumer_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'refresh_tokens', COUNT(*) FROM refresh_tokens WHERE jti LIKE 'lt-jti-%'
UNION ALL SELECT 'audit_logs (eventos)', COUNT(*) FROM audit_logs WHERE action = 'LT_SEED_EVENT'
UNION ALL SELECT 'ads', COUNT(*) FROM ads WHERE title LIKE 'LT Ad %'
UNION ALL SELECT 'ad_assets', COUNT(*) FROM ad_assets aa JOIN ads a ON a.id = aa.ad_id WHERE a.title LIKE 'LT Ad %'
UNION ALL SELECT 'campaigns', COUNT(*) FROM campaigns WHERE JSON_EXTRACT(config_data, '$.loadtest') IS NOT NULL
UNION ALL SELECT 'branding_requests', COUNT(*) FROM branding_requests WHERE brand_name LIKE 'LT Brand %'
UNION ALL SELECT 'branding_request_comments', COUNT(*) FROM branding_request_comments c JOIN branding_requests b ON b.id = c.branding_request_id WHERE b.brand_name LIKE 'LT Brand %'
UNION ALL SELECT 'surveys', COUNT(*) FROM surveys WHERE title LIKE 'LT Survey %'
UNION ALL SELECT 'survey_questions', COUNT(*) FROM survey_questions q JOIN surveys s ON s.id = q.survey_id WHERE s.title LIKE 'LT Survey %'
UNION ALL SELECT 'question_options', COUNT(*) FROM question_options o JOIN survey_questions q ON q.id = o.question_id JOIN surveys s ON s.id = q.survey_id WHERE s.title LIKE 'LT Survey %'
UNION ALL SELECT 'products', COUNT(*) FROM products WHERE name LIKE 'LT Product %'
UNION ALL SELECT 'product_stock', COUNT(*) FROM product_stock ps JOIN products p ON p.id = ps.product_id WHERE p.name LIKE 'LT Product %'
UNION ALL SELECT 'product_stock vendido', COUNT(*) FROM product_stock ps JOIN products p ON p.id = ps.product_id WHERE p.name LIKE 'LT Product %' AND ps.status = 'SOLD'
UNION ALL SELECT 'budget_transactions', COUNT(*) FROM budget_transactions WHERE reference_id LIKE 'lt-bt-%'
UNION ALL SELECT 'wallets', COUNT(*) FROM wallets w JOIN users u ON u.id = w.commercial_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'subscriptions', COUNT(*) FROM subscriptions WHERE wompi_reference LIKE 'LT-SUB-%'
UNION ALL SELECT 'investments', COUNT(*) FROM investments WHERE wompi_reference LIKE 'LT-INV-%'
UNION ALL SELECT 'commercial_onboarding', COUNT(*) FROM commercial_onboarding co JOIN users u ON u.id = co.commercial_details_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'commercial_contracts', COUNT(*) FROM commercial_contracts cc JOIN users u ON u.id = cc.commercial_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'commercial_documents', COUNT(*) FROM commercial_documents d JOIN commercial_onboarding co ON co.id = d.commercial_onboarding_id JOIN users u ON u.id = co.commercial_details_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'catalog_integration_requests', COUNT(*) FROM catalog_integration_requests r JOIN users u ON u.id = r.commercial_id WHERE u.email REGEXP @lt_seeded_email
UNION ALL SELECT 'prosperity_accounts', COUNT(*) FROM prosperity_accounts pa JOIN users u ON u.id = pa.commercial_id WHERE u.email REGEXP @lt_seeded_email;

SELECT '== 4. Globales: rifas y premios' AS seccion;
SELECT 'rifas activas' AS dato, COUNT(*) AS valor FROM raffles WHERE title LIKE 'LT Raffle A%'
UNION ALL SELECT 'rifas finalizadas', COUNT(*) FROM raffles WHERE title LIKE 'LT Raffle C%'
UNION ALL SELECT 'premios', COUNT(*) FROM raffle_prizes WHERE title LIKE 'LT Prize %'
UNION ALL SELECT 'ganadores pendientes de reclamar (1 % de los consumidores)', COUNT(*) FROM raffle_winners w JOIN users u ON u.id = w.winner_consumer_id WHERE u.email LIKE 'lt-consumer-%@loadtest.invalid' AND w.prize_claimed = 0
UNION ALL SELECT 'avisos de mascotas', COUNT(*) FROM pet_notifications WHERE external_id LIKE 'lt-pet-%'
UNION ALL SELECT 'historias de impacto publicadas', COUNT(*) FROM impact_stories WHERE title LIKE 'LT Story %' AND status = 'PUBLISHED'
UNION ALL SELECT 'compras de historial con delivered_code cifrado', COUNT(*) FROM purchase_items i JOIN purchases p ON p.id = i.purchase_id WHERE p.reference_id LIKE 'LT-PUR-%' AND i.delivered_code IS NOT NULL AND i.delivered_code NOT LIKE 'RAW:%';

SELECT '== 4b. Tesorería (centavos; esperado A: reserva 4.700.000.000, pagos 1.000.000.000, operación 500.000.000; B: 47.450.000.000, 10.000.000.000 y 5.000.000.000)' AS seccion;
SELECT code AS cuenta, balance_cents AS saldo_centavos
FROM treasury_accounts
WHERE code IN ('KEYS_RESERVE', 'PAYOUTS_PENDING', 'OPERATIONS')
ORDER BY FIELD(code, 'KEYS_RESERVE', 'PAYOUTS_PENDING', 'OPERATIONS');

SELECT '== 5. Integridad (todo tiene que dar 0)' AS seccion;
SELECT 'consumidores sin ninguna categoría de preferencia' AS control, COUNT(*) AS violaciones
FROM users u
WHERE u.email LIKE 'lt-consumer-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM consumer_preferences cp WHERE cp.user_id = u.id)
UNION ALL
SELECT 'audiencias del pool sin ninguna categoría', COUNT(*)
FROM target_audiences ta
WHERE ta.min_age = 18 AND ta.target_gender = 'ALL' AND ta.max_age BETWEEN 81 AND 92
  AND NOT EXISTS (SELECT 1 FROM target_audience_categories tc WHERE tc.target_audience_id = ta.id)
UNION ALL
SELECT 'anuncios sin audiencia o con una audiencia sin categorías', COUNT(*)
FROM ads a
WHERE a.title LIKE 'LT Ad %'
  AND (a.target_audience_id IS NULL
       OR NOT EXISTS (SELECT 1 FROM target_audience_categories tc WHERE tc.target_audience_id = a.target_audience_id))
UNION ALL
SELECT 'usuarios sembrados con correo fuera de @loadtest.invalid', COUNT(*)
FROM users u
WHERE u.email LIKE 'lt-%' AND u.email NOT LIKE '%@loadtest.invalid'
UNION ALL
SELECT 'códigos de stock aún sin cifrar (RAW:)', COUNT(*) FROM product_stock WHERE code LIKE 'RAW:%' OR code_hash LIKE 'RAW:%'
UNION ALL
SELECT 'códigos de premios aún sin cifrar (RAW:)', COUNT(*) FROM raffle_prizes WHERE claim_code LIKE 'RAW:%'
UNION ALL
SELECT 'compras de historial sin delivered_code (o sin cifrar)', COUNT(*)
FROM purchase_items i JOIN purchases p ON p.id = i.purchase_id
WHERE p.reference_id LIKE 'LT-PUR-%' AND (i.delivered_code IS NULL OR i.delivered_code LIKE 'RAW:%')
UNION ALL
SELECT 'delivered_code aún sin cifrar (RAW:) en cualquier compra', COUNT(*) FROM purchase_items WHERE delivered_code LIKE 'RAW:%'
UNION ALL
SELECT 'cuentas de tesorería (reserva, pagos, operación) con saldo 0', COUNT(*)
FROM treasury_accounts WHERE code IN ('KEYS_RESERVE', 'PAYOUTS_PENDING', 'OPERATIONS') AND balance_cents <= 0
UNION ALL
SELECT 'historias de impacto ficticias publicadas por debajo de 10', GREATEST(10 - COUNT(*), 0)
FROM impact_stories WHERE title LIKE 'LT Story %' AND status = 'PUBLISHED'
UNION ALL
SELECT 'consumidores sin billetera de llaves', COUNT(*)
FROM users u
WHERE u.email LIKE 'lt-consumer-%@loadtest.invalid'
  AND NOT EXISTS (SELECT 1 FROM key_wallets kw WHERE kw.consumer_id = u.id)
UNION ALL
SELECT 'comerciales sin billetera ni plan', COUNT(*)
FROM users u
JOIN commercial_details cd ON cd.user_id = u.id
WHERE u.email LIKE 'lt-commercial-%@loadtest.invalid'
  AND (cd.current_plan_id IS NULL OR NOT EXISTS (SELECT 1 FROM wallets w WHERE w.commercial_id = u.id));
