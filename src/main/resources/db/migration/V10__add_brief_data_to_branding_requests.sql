-- Esquema que la rama Nico agregaba con ddl-auto: update y que ninguna migración
-- creaba. Con ddl-auto: validate, sin esto la app no arranca.
--
-- Cada ADD COLUMN es condicional porque las bases de desarrollo que corrieron con
-- ddl-auto: update ya tienen las columnas, y MySQL no soporta ADD COLUMN IF NOT
-- EXISTS: sin el chequeo, esta migración fallaría ahí y la app no arrancaría.

-- ─── 1. Contenido de marca de la solicitud de brandeo ───────────────────────────

-- Lo que carga el anunciante en la solicitud (BrandingRequest.briefData): las
-- preguntas de la trivia, las palabras de la sopa de letras, las pistas del
-- crucigrama. Va aparte de draft_form_data, que es del diseñador.
SET @brief_data_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'branding_requests'
      AND column_name = 'brief_data'
);

SET @ddl := IF(@brief_data_exists = 0,
    'ALTER TABLE branding_requests ADD COLUMN brief_data json DEFAULT NULL AFTER draft_form_data',
    'SELECT 1');

PREPARE add_brief_data FROM @ddl;
EXECUTE add_brief_data;
DEALLOCATE PREPARE add_brief_data;

-- ─── 2. Monto acreditado y liquidación de la emisión de llaves ──────────────────
-- credited_amount: lo que se acreditó de verdad al consumidor (reward × multiplicador
-- de nivel). Nullable: las filas anteriores no tienen el dato y no se puede
-- reconstruir desde la fila.
--
-- issuance_settled: si el diferencial (financiado − acreditado) ya se liquidó en
-- tesorería. Lo marca KeyIssuanceSettlementService por lotes. DEFAULT 0 es seguro
-- para las filas viejas: las consultas de liquidación exigen credited_amount IS NOT
-- NULL, así que el historial nunca entra al cálculo.

-- ad_likes.credited_amount
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ad_likes' AND column_name = 'credited_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE ad_likes ADD COLUMN credited_amount bigint DEFAULT NULL AFTER reward_amount',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ad_likes.issuance_settled
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'ad_likes' AND column_name = 'issuance_settled'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE ad_likes ADD COLUMN issuance_settled bit(1) NOT NULL DEFAULT b''0'' AFTER credited_amount',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- survey_rewards.credited_amount
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'survey_rewards' AND column_name = 'credited_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE survey_rewards ADD COLUMN credited_amount bigint DEFAULT NULL AFTER amount',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- survey_rewards.issuance_settled
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'survey_rewards' AND column_name = 'issuance_settled'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE survey_rewards ADD COLUMN issuance_settled bit(1) NOT NULL DEFAULT b''0'' AFTER credited_amount',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── 3. Conceptos de tesorería de la emisión de llaves y del juego de mascotas ──
-- MovementConcept ganó KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS, KEYS_ISSUANCE_DEFICIT_FUNDING
-- y PET_GAME_KEYS_TO_OPERATIONS. Sin ellos en el ENUM, validate no se queja pero el
-- INSERT del movimiento falla en tiempo de ejecución ("Data truncated for column").
--
-- MODIFY ya es idempotente: redefine la lista completa. Es la lista de V3 más las
-- tres nuevas, en el orden alfabético que usa Hibernate.
ALTER TABLE treasury_movements MODIFY COLUMN concept
    ENUM('BASIC_PLAN_SUBSCRIPTION','BASIC_PLAN_SUBSCRIPTION_VAT','BUSINESS_DEPOSIT_FORTIFICATION',
         'BUSINESS_DEPOSIT_KEYS','BUSINESS_DEPOSIT_OPERATIONS','BUSINESS_DEPOSIT_VAT',
         'COMMISSION_RETENTION','COMMISSION_REVERSAL','COMMISSION_VAT_RETENTION','COMMISSION_VAT_REVERSAL',
         'COPAYMENT_KEYS_CONVERSION','EXPIRED_KEYS_TO_FORTIFICATION','FORTIFICATION_PURCHASE',
         'KEYS_ISSUANCE_DEFICIT_FUNDING','KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS',
         'PAYOUT_TO_BUSINESS','PET_GAME_KEYS_TO_OPERATIONS',
         'REFUND_CASH_TO_OPERATIONS','REFUND_KEYS_TO_RESERVE','REFUND_TO_BUYER',
         'SALE_TO_PAYOUT_PENDING') NOT NULL;
