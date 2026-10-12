-- Finanzas demo beta: saldos de llaves, ledger de recompensas, wallet comercial
-- y payout de marketplace. Todos los importes están en centavos de COP.
--
-- No llama a Wompi ni a Random.org. Los registros de transacción de pago son
-- fixtures locales identificados en metadata como BETA_SEED_FIXTURE; la
-- configuración de la app usa las credenciales y endpoints sandbox de beta.
--
-- 1 llave = financial.key-value-cents (en dev, 1000 centavos = $10 COP).
-- El saldo de cada key_wallet se reconcilia con la suma de key_transactions:
-- 75% compra y 25% conectividad, igual que KeyWalletServiceImpl.
-- Las interacciones fixture se marcan issuance_settled porque sus movimientos
-- ya se crean directamente abajo; no deben entrar al settlement real de tesorería.
--
-- El premio del sorteo finalizado es un premio ficticio, no un crédito de llaves:
-- el enum KeyTransactionType no define crédito por ganar rifas. La actividad de
-- sorteo se representa con sus tickets/participación/ganador en el seed de rifas.
--
-- Para borrar estos datos, primero eliminar las filas hijas:
-- payout_items, payouts, copayments, key_transactions, survey_rewards,
-- game_sessions, campaigns, investments, wallets, key_wallets, wompi_transactions.

-- Wallet de llaves para cada consumidor congelado de beta.
INSERT INTO key_wallets (
    id,
    blocked_connectivity_keys_cents,
    blocked_purchase_keys_cents,
    connectivity_keys_cents,
    created_at,
    purchase_keys_cents,
    updated_at,
    consumer_id
)
SELECT
    UUID_TO_BIN(CONCAT('0be7a100', SUBSTRING(BIN_TO_UUID(consumer_user.public_id), 9))),
    0,
    0,
    0,
    NOW(),
    0,
    NOW(),
    consumer_details.user_id
FROM users consumer_user
JOIN consumer_details
  ON consumer_details.user_id = consumer_user.id
WHERE consumer_user.public_id >= UUID_TO_BIN('0be7a000-0005-0000-0000-000000000001')
  AND consumer_user.public_id <= UUID_TO_BIN('0be7a000-0005-0000-0000-00000000000a')
  AND NOT EXISTS (
      SELECT 1
      FROM key_wallets existing_wallet
      WHERE existing_wallet.consumer_id = consumer_details.user_id
  );

-- La partida demo usa el Trivia Quiz ya incluido en db/seed/games.
INSERT INTO campaigns (
    id,
    game_id,
    config_definition_id,
    config_data,
    commercial_id,
    score_reward_factor,
    average_reward_per_session_cents,
    completion_reward_cents,
    max_reward_per_session_cents,
    budget_cents,
    spent_cents,
    max_session_per_user_per_day,
    start_date,
    end_date,
    target_audience_id,
    status,
    created_at,
    updated_at,
    version,
    sessions_played,
    completed_sessions,
    total_play_time_seconds,
    unique_players_count
)
SELECT
    970001,
    game.id,
    config_definition.id,
    JSON_OBJECT('source', 'BETA_SEED_FIXTURE', 'brand', 'Ecosistema Andino'),
    commercial_details.user_id,
    1.0,
    10000,
    5000,
    20000,
    1000000,
    5100,
    3,
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    NULL,
    'ACTIVE',
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    NOW(),
    0,
    1,
    1,
    120,
    1
FROM games game
JOIN game_config_definitions config_definition
  ON config_definition.game_id = game.id
 AND config_definition.version = 1
JOIN users commercial_user
  ON commercial_user.public_id = UUID_TO_BIN('0be7a000-0004-0000-0000-000000000003')
JOIN commercial_details
  ON commercial_details.user_id = commercial_user.id
WHERE game.id = 19
  AND NOT EXISTS (
      SELECT 1 FROM campaigns existing_campaign WHERE existing_campaign.id = 970001
  );

-- Completa la marca de la campaña demo para que GET /campaigns/970001 devuelva
-- brandName, campaignGoal y brandingRequestId; el mapper obtiene esos datos de
-- branding_requests, no de campaigns.config_data.
INSERT INTO branding_requests (
    brand_name,
    brand_description,
    budget_cents,
    campaign_goal,
    created_at,
    draft_form_data,
    end_date,
    game_config,
    max_sessions_per_user_per_day,
    start_date,
    status,
    target_url,
    updated_at,
    commercial_id,
    game_id,
    game_config_definition_id,
    reviewed_by_admin_id,
    campaign_id
)
SELECT
    'Ecosistema Andino',
    'Marca colombiana de soluciones prácticas y sostenibles para el hogar.',
    campaign.budget_cents,
    'BRAND_AWARENESS',
    campaign.created_at,
    JSON_OBJECT(),
    campaign.end_date,
    campaign.config_data,
    campaign.max_session_per_user_per_day,
    campaign.start_date,
    'CAMPAIGN_CREATED',
    'https://ecosistemaandino.co/soluciones',
    campaign.updated_at,
    campaign.commercial_id,
    campaign.game_id,
    campaign.config_definition_id,
    admin_details.user_id,
    campaign.id
FROM campaigns campaign
JOIN users admin_user
  ON admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
JOIN admin_details
  ON admin_details.user_id = admin_user.id
WHERE campaign.id = 970001
  AND NOT EXISTS (
      SELECT 1
      FROM branding_requests existing_request
      WHERE existing_request.campaign_id = campaign.id
  );

-- Segmentación: en el flujo real una campaña nunca llega a CAMPAIGN_CREATED sin
-- audiencia completa (BrandingRequest.hasCompleteTargeting()). Sin esto,
-- campaigns.target_audience_id y branding_requests.target_audience_id quedan
-- NULL y el GET de detalle de campaña devuelve categories/targetGender/
-- minAge/maxAge/targetMunicipalities ausentes — el front los asume siempre
-- presentes y falla al renderizar el detalle (sin que se note en Network).
-- Reutiliza la audiencia 20-45 ALL/Tecnología ya creada en
-- beta_campaigns_surveys.sql (misma que usan los anuncios de Ecosistema Andino).
UPDATE campaigns
SET target_audience_id = (
    SELECT ta.id
    FROM target_audiences ta
    WHERE ta.min_age = 20 AND ta.max_age = 45 AND ta.target_gender = 'ALL'
    LIMIT 1
)
WHERE id = 970001
  AND target_audience_id IS NULL;

UPDATE branding_requests
SET target_audience_id = (
    SELECT ta.id
    FROM target_audiences ta
    WHERE ta.min_age = 20 AND ta.max_age = 45 AND ta.target_gender = 'ALL'
    LIMIT 1
)
WHERE campaign_id = 970001
  AND target_audience_id IS NULL;

INSERT INTO game_sessions (
    session_token,
    user_hash,
    consumer_id,
    game_id,
    campaign_id,
    start_time,
    end_time,
    coins_earned,
    credited_amount,
    issuance_settled,
    play_time_seconds,
    device_platform,
    completed,
    reward_granted,
    score
)
SELECT
    'beta-seed-trivia-session-001',
    consumer_details.user_hash,
    consumer_details.user_id,
    game.id,
    campaign.id,
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 120 SECOND,
    5100,
    5100,
    TRUE,
    120,
    'MOBILE',
    TRUE,
    TRUE,
    100
FROM users consumer_user
JOIN consumer_details
  ON consumer_details.user_id = consumer_user.id
JOIN campaigns campaign
  ON campaign.id = 970001
JOIN games game
  ON game.id = campaign.game_id
WHERE consumer_user.public_id = UUID_TO_BIN('0be7a000-0005-0000-0000-000000000006')
  AND NOT EXISTS (
      SELECT 1
      FROM game_sessions existing_session
      WHERE existing_session.session_token = 'beta-seed-trivia-session-001'
  );

-- La recompensa de cada encuesta completada equivale a preguntas × tarifa
-- financiada por pregunta; el valor acreditado aplica el multiplicador del nivel.
INSERT INTO survey_rewards (
    amount,
    credited_amount,
    issuance_settled,
    granted_at,
    processed_at,
    status,
    session_id
)
SELECT
    question_count.question_count * survey.reward_amount_per_question_cents,
    ROUND(question_count.question_count * survey.reward_amount_per_question_cents * (
        CASE profile.current_level
            WHEN 'BRONCE' THEN 0.5
            WHEN 'PLATA' THEN 0.6
            WHEN 'ORO' THEN 0.7
            WHEN 'RUBI' THEN 0.8
            WHEN 'ESMERALDA' THEN 0.9
            WHEN 'DIAMANTE' THEN 1.0
        END
    )),
    TRUE,
    survey_session.completed_at,
    survey_session.completed_at,
    'PROCESSED',
    survey_session.id
FROM survey_sessions survey_session
JOIN surveys survey
  ON survey.id = survey_session.survey_id
JOIN users survey_consumer
  ON survey_consumer.id = survey_session.consumer_id
JOIN user_level_profile profile
  ON profile.consumer_id = survey_session.consumer_id
JOIN (
    SELECT survey_id, COUNT(*) AS question_count
    FROM survey_questions
    GROUP BY survey_id
) question_count
  ON question_count.survey_id = survey.id
WHERE survey_session.status = 'COMPLETED'
  AND survey_consumer.public_id IN (
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000004'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000005'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000008')
  )
  AND survey.title IN (
      'Encuesta sobre compras en panaderías locales',
      'Lo que buscas en la moda cotidiana'
  )
  AND NOT EXISTS (
      SELECT 1
      FROM survey_rewards existing_reward
      WHERE existing_reward.session_id = survey_session.id
  );

UPDATE survey_rewards survey_reward
JOIN survey_sessions survey_session
  ON survey_session.id = survey_reward.session_id
JOIN surveys survey
  ON survey.id = survey_session.survey_id
JOIN users survey_consumer
  ON survey_consumer.id = survey_session.consumer_id
SET survey_reward.issuance_settled = TRUE
WHERE survey_session.status = 'COMPLETED'
  AND survey_reward.status = 'PROCESSED'
  AND survey_consumer.public_id IN (
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000004'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000005'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000008')
  )
  AND survey.title IN (
      'Encuesta sobre compras en panaderías locales',
      'Lo que buscas en la moda cotidiana'
  );

-- Persistir lo realmente acreditado por cada interacción patrocinada.
UPDATE ad_likes ad_like
JOIN user_level_profile profile
  ON profile.consumer_id = ad_like.consumer_user_id
JOIN users ad_consumer
  ON ad_consumer.id = ad_like.consumer_user_id
SET ad_like.credited_amount = ROUND(ad_like.reward_amount * (
        CASE profile.current_level
            WHEN 'BRONCE' THEN 0.5
            WHEN 'PLATA' THEN 0.6
            WHEN 'ORO' THEN 0.7
            WHEN 'RUBI' THEN 0.8
            WHEN 'ESMERALDA' THEN 0.9
            WHEN 'DIAMANTE' THEN 1.0
        END
    )),
    ad_like.issuance_settled = TRUE
WHERE ad_like.ad_id IN (
    SELECT demo_ad.id
    FROM ads demo_ad
    WHERE demo_ad.title IN (
        'Desayunos con sabor a hogar',
        'Pan artesanal para compartir',
        'Estilo urbano para todos los días',
        'Tecnología útil para una vida más simple'
    )
)
  AND ad_consumer.public_id IN (
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000004'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000005'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000006'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000008'),
      UUID_TO_BIN('0be7a000-0005-0000-0000-000000000009')
);

UPDATE game_sessions game_session
JOIN user_level_profile profile
  ON profile.consumer_id = game_session.consumer_id
SET game_session.credited_amount = ROUND(game_session.coins_earned * (
        CASE profile.current_level
            WHEN 'BRONCE' THEN 0.5
            WHEN 'PLATA' THEN 0.6
            WHEN 'ORO' THEN 0.7
            WHEN 'RUBI' THEN 0.8
            WHEN 'ESMERALDA' THEN 0.9
            WHEN 'DIAMANTE' THEN 1.0
        END
    )),
    game_session.issuance_settled = TRUE
WHERE game_session.session_token = 'beta-seed-trivia-session-001';

-- Cada evento genera los mismos dos asientos CREDIT_INTERACTION que los flujos
-- de producción: compra y conectividad. MD5 genera IDs UUID deterministas para
-- que una reejecución no duplique asientos, aunque las PK de origen sean AUTO_INCREMENT.
INSERT INTO key_transactions (
    id,
    connectivity_keys_delta_cents,
    created_at,
    expires_at,
    expiry_processed,
    purchase_keys_delta_cents,
    reason,
    reference_id,
    type,
    key_wallet_id
)
SELECT
    UUID_TO_BIN(CONCAT(
        SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 1, 8), '-',
        SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 9, 4), '-',
        SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 13, 4), '-',
        SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 17, 4), '-',
        SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 21, 12)
    )),
    CASE
        WHEN component.component_name = 'CONNECTIVITY'
        THEN event_record.credited_cents - ROUND(event_record.credited_cents * 0.75)
        ELSE NULL
    END,
    event_record.event_at,
    CASE
        WHEN component.component_name = 'CONNECTIVITY'
        THEN DATE_ADD(event_record.event_at, INTERVAL 1 DAY)
        ELSE DATE_ADD(LAST_DAY(event_record.event_at), INTERVAL 1 DAY) + INTERVAL 5 HOUR
    END,
    FALSE,
    CASE
        WHEN component.component_name = 'PURCHASE'
        THEN ROUND(event_record.credited_cents * 0.75)
        ELSE NULL
    END,
    event_record.reason,
    UUID_TO_BIN(CONCAT(
        SUBSTRING(MD5(event_record.event_key), 1, 8), '-',
        SUBSTRING(MD5(event_record.event_key), 9, 4), '-',
        SUBSTRING(MD5(event_record.event_key), 13, 4), '-',
        SUBSTRING(MD5(event_record.event_key), 17, 4), '-',
        SUBSTRING(MD5(event_record.event_key), 21, 12)
    )),
    'CREDIT_INTERACTION',
    key_wallet.id
FROM (
    SELECT
        CONCAT('BETA-AD-', ad_like.ad_id, '-', ad_like.consumer_user_id) AS event_key,
        ad_like.consumer_user_id AS consumer_id,
        ad_like.credited_amount AS credited_cents,
        CONCAT('Interacción con anuncio #', ad_like.ad_id) AS reason,
        ad_like.created_at AS event_at
    FROM ad_likes ad_like
    JOIN ads demo_ad
      ON demo_ad.id = ad_like.ad_id
    JOIN users demo_consumer
      ON demo_consumer.id = ad_like.consumer_user_id
    WHERE demo_ad.title IN (
        'Desayunos con sabor a hogar',
        'Pan artesanal para compartir',
        'Estilo urbano para todos los días',
        'Tecnología útil para una vida más simple'
    )
      AND demo_consumer.public_id IN (
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000004'),
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000005'),
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000006'),
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000008'),
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000009')
      )
    UNION ALL
    SELECT
        CONCAT('BETA-SURVEY-', survey_session.id),
        survey_session.consumer_id,
        survey_reward.credited_amount,
        CONCAT('Encuesta completada #', survey.id),
        survey_session.completed_at
    FROM survey_rewards survey_reward
    JOIN survey_sessions survey_session
      ON survey_session.id = survey_reward.session_id
    JOIN surveys survey
      ON survey.id = survey_session.survey_id
    JOIN users demo_consumer
      ON demo_consumer.id = survey_session.consumer_id
    WHERE survey.title IN (
        'Encuesta sobre compras en panaderías locales',
        'Lo que buscas en la moda cotidiana'
    )
      AND survey_reward.status = 'PROCESSED'
      AND demo_consumer.public_id IN (
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000004'),
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000005'),
          UUID_TO_BIN('0be7a000-0005-0000-0000-000000000008')
      )
    UNION ALL
    SELECT
        CONCAT('BETA-GAME-', game_session.id),
        game_session.consumer_id,
        game_session.credited_amount,
        CONCAT('Sesión de juego completada #', game_session.id),
        game_session.end_time
    FROM game_sessions game_session
    WHERE game_session.session_token = 'beta-seed-trivia-session-001'
      AND game_session.reward_granted = TRUE
) event_record
JOIN key_wallets key_wallet
  ON key_wallet.consumer_id = event_record.consumer_id
CROSS JOIN (
    SELECT 'PURCHASE' AS component_name
    UNION ALL SELECT 'CONNECTIVITY'
) component
WHERE event_record.credited_cents > 0
  AND NOT EXISTS (
      SELECT 1
      FROM key_transactions existing_transaction
      WHERE existing_transaction.id = UUID_TO_BIN(CONCAT(
          SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 1, 8), '-',
          SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 9, 4), '-',
          SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 13, 4), '-',
          SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 17, 4), '-',
          SUBSTRING(MD5(CONCAT(event_record.event_key, '/', component.component_name)), 21, 12)
      ))
  );

-- Reconciliar saldos de compra/conectividad con el ledger, sin asumir escalas
-- distintas de centavos de COP.
UPDATE key_wallets key_wallet
SET key_wallet.purchase_keys_cents = (
        SELECT COALESCE(SUM(key_entry.purchase_keys_delta_cents), 0)
        FROM key_transactions key_entry
        WHERE key_entry.key_wallet_id = key_wallet.id
    ),
    key_wallet.connectivity_keys_cents = (
        SELECT COALESCE(SUM(key_entry.connectivity_keys_delta_cents), 0)
        FROM key_transactions key_entry
        WHERE key_entry.key_wallet_id = key_wallet.id
    ),
    key_wallet.updated_at = NOW()
WHERE key_wallet.consumer_id IN (
    SELECT consumer_details.user_id
    FROM consumer_details
    JOIN users consumer_user ON consumer_user.id = consumer_details.user_id
    WHERE consumer_user.public_id >= UUID_TO_BIN('0be7a000-0005-0000-0000-000000000001')
      AND consumer_user.public_id <= UUID_TO_BIN('0be7a000-0005-0000-0000-00000000000a')
);

-- Wallets de presupuesto con saldos ficticios para los comerciales congelados.
-- No representan cobros ni inversiones reales; no se crea ninguna transacción
-- Wompi ni se ejecuta una transferencia.
INSERT INTO wallets (
    balance_cents,
    created_at,
    last_budget_alert_stage,
    last_deposit_amount_cents,
    last_updated,
    status,
    version,
    commercial_id
)
SELECT
    CASE plan.code
        WHEN 'BASIC' THEN 25000000
        WHEN 'STANDARD' THEN 150000000
        WHEN 'PREMIUM' THEN 1500000000
    END,
    NOW(),
    'NONE',
    CASE plan.code
        WHEN 'BASIC' THEN 25000000
        WHEN 'STANDARD' THEN 150000000
        WHEN 'PREMIUM' THEN 1500000000
    END,
    NOW(),
    'ACTIVE',
    0,
    commercial_details.user_id
FROM users commercial_user
JOIN commercial_details
  ON commercial_details.user_id = commercial_user.id
JOIN plans plan
  ON plan.id = commercial_details.current_plan_id
WHERE commercial_user.public_id IN (
    UUID_TO_BIN('0be7a000-0004-0000-0000-000000000001'),
    UUID_TO_BIN('0be7a000-0004-0000-0000-000000000002'),
    UUID_TO_BIN('0be7a000-0004-0000-0000-000000000003')
)
  AND NOT EXISTS (
      SELECT 1
      FROM wallets existing_wallet
      WHERE existing_wallet.commercial_id = commercial_details.user_id
  );

-- Compra digital beta: pago y payout ficticios para el ítem ya reclamado.
INSERT INTO wompi_transactions (
    id,
    amount_in_cents,
    created_at,
    currency,
    metadata,
    reference,
    status,
    type,
    updated_at,
    wompi_created_at,
    wompi_id
)
SELECT
    UUID_TO_BIN('b37a0001-0000-4000-8000-000000000001'),
    purchase.total_cents,
    purchase.completed_at,
    'COP',
    JSON_OBJECT('source', 'BETA_SEED_FIXTURE', 'provider_call', FALSE),
    'BETA-SEED-CHARGE-MARKETPLACE-DIGITAL-001',
    'APPROVED',
    'CHARGE_COPAYMENT',
    purchase.completed_at,
    purchase.completed_at,
    'BETA-SIMULATED-CHARGE-DIGITAL-001'
FROM purchases purchase
WHERE purchase.reference_id = 'BETA-MARKETPLACE-DIGITAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM wompi_transactions existing_transaction
      WHERE existing_transaction.reference = 'BETA-SEED-CHARGE-MARKETPLACE-DIGITAL-001'
  );

INSERT INTO wompi_transactions (
    id,
    amount_in_cents,
    created_at,
    currency,
    metadata,
    reference,
    status,
    type,
    updated_at,
    wompi_created_at,
    wompi_id
)
SELECT
    UUID_TO_BIN('b37a0001-0000-4000-8000-000000000003'),
    purchase.total_cents,
    purchase.completed_at,
    'COP',
    JSON_OBJECT('source', 'BETA_SEED_FIXTURE', 'provider_call', FALSE),
    'BETA-SEED-CHARGE-MARKETPLACE-PHYSICAL-001',
    'APPROVED',
    'CHARGE_COPAYMENT',
    purchase.completed_at,
    purchase.completed_at,
    'BETA-SIMULATED-CHARGE-PHYSICAL-001'
FROM purchases purchase
WHERE purchase.reference_id = 'BETA-MARKETPLACE-PHYSICAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM wompi_transactions existing_transaction
      WHERE existing_transaction.reference = 'BETA-SEED-CHARGE-MARKETPLACE-PHYSICAL-001'
  );

INSERT INTO wompi_transactions (
    id,
    amount_in_cents,
    created_at,
    currency,
    metadata,
    reference,
    status,
    type,
    updated_at,
    wompi_created_at,
    wompi_id
)
SELECT
    UUID_TO_BIN('b37a0001-0000-4000-8000-000000000002'),
    purchase_item.net_to_commercial_cents,
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    'COP',
    JSON_OBJECT('source', 'BETA_SEED_FIXTURE', 'provider_call', FALSE),
    'BETA-SEED-PAYOUT-MARKETPLACE-DIGITAL-001',
    'APPROVED',
    'TRANSFER_PAYOUT',
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    'BETA-SIMULATED-PAYOUT-DIGITAL-001'
FROM purchases purchase
JOIN purchase_items purchase_item
  ON purchase_item.purchase_id = purchase.id
 AND purchase_item.status = 'CLAIMED'
WHERE purchase.reference_id = 'BETA-MARKETPLACE-DIGITAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM wompi_transactions existing_transaction
      WHERE existing_transaction.reference = 'BETA-SEED-PAYOUT-MARKETPLACE-DIGITAL-001'
  );

INSERT INTO copayments (
    id,
    cash_amount_cents,
    created_at,
    keys_refunded_at,
    keys_used,
    keys_value_cents,
    status,
    total_amount_cents,
    consumer_id,
    purchase_id,
    wompi_transaction_id
)
SELECT
    UUID_TO_BIN('b37a0002-0000-4000-8000-000000000001'),
    purchase.total_cents,
    purchase.completed_at,
    NULL,
    0,
    0,
    'COMPLETED',
    purchase.total_cents,
    purchase.consumer_id,
    purchase.id,
    wompi_transaction.id
FROM purchases purchase
JOIN wompi_transactions wompi_transaction
  ON wompi_transaction.reference = 'BETA-SEED-CHARGE-MARKETPLACE-DIGITAL-001'
WHERE purchase.reference_id = 'BETA-MARKETPLACE-DIGITAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM copayments existing_copayment
      WHERE existing_copayment.purchase_id = purchase.id
  );

INSERT INTO copayments (
    id,
    cash_amount_cents,
    created_at,
    keys_refunded_at,
    keys_used,
    keys_value_cents,
    status,
    total_amount_cents,
    consumer_id,
    purchase_id,
    wompi_transaction_id
)
SELECT
    UUID_TO_BIN('b37a0002-0000-4000-8000-000000000002'),
    purchase.total_cents,
    purchase.completed_at,
    NULL,
    0,
    0,
    'COMPLETED',
    purchase.total_cents,
    purchase.consumer_id,
    purchase.id,
    wompi_transaction.id
FROM purchases purchase
JOIN wompi_transactions wompi_transaction
  ON wompi_transaction.reference = 'BETA-SEED-CHARGE-MARKETPLACE-PHYSICAL-001'
WHERE purchase.reference_id = 'BETA-MARKETPLACE-PHYSICAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM copayments existing_copayment
      WHERE existing_copayment.purchase_id = purchase.id
  );

INSERT INTO payouts (
    id,
    commission_cents,
    commission_amount_cents,
    commission_pct_applied,
    created_at,
    gross_amount_cents,
    net_amount_cents,
    paid_at,
    period_start,
    period_end,
    retry_count,
    scheduled_at,
    status,
    commercial_id,
    wompi_transaction_id
)
SELECT
    UUID_TO_BIN('b37a0003-0000-4000-8000-000000000001'),
    purchase_item.commission_cents,
    purchase_item.commission_cents,
    purchase_item.commission_pct_applied,
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    purchase_item.subtotal_cents,
    purchase_item.net_to_commercial_cents,
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    purchase.completed_at,
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    0,
    DATE_ADD(purchase.completed_at, INTERVAL 1 DAY),
    'PAID',
    purchase_item.commercial_id,
    wompi_transaction.id
FROM purchases purchase
JOIN purchase_items purchase_item
  ON purchase_item.purchase_id = purchase.id
 AND purchase_item.status = 'CLAIMED'
JOIN wompi_transactions wompi_transaction
  ON wompi_transaction.reference = 'BETA-SEED-PAYOUT-MARKETPLACE-DIGITAL-001'
WHERE purchase.reference_id = 'BETA-MARKETPLACE-DIGITAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM payouts existing_payout
      WHERE existing_payout.id = UUID_TO_BIN('b37a0003-0000-4000-8000-000000000001')
  );

INSERT INTO payout_items (
    id,
    amount_cents,
    copayment_id,
    payout_id,
    purchase_item_id
)
SELECT
    UUID_TO_BIN('b37a0004-0000-4000-8000-000000000001'),
    purchase_item.net_to_commercial_cents,
    copayment.id,
    payout.id,
    purchase_item.id
FROM purchases purchase
JOIN purchase_items purchase_item
  ON purchase_item.purchase_id = purchase.id
 AND purchase_item.status = 'CLAIMED'
JOIN copayments copayment
  ON copayment.purchase_id = purchase.id
JOIN payouts payout
  ON payout.id = UUID_TO_BIN('b37a0003-0000-4000-8000-000000000001')
WHERE purchase.reference_id = 'BETA-MARKETPLACE-DIGITAL-001'
  AND NOT EXISTS (
      SELECT 1
      FROM payout_items existing_payout_item
      WHERE existing_payout_item.purchase_item_id = purchase_item.id
  );
