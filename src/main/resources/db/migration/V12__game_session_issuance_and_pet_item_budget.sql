-- Llaves de los juegos y cobro por uso de los ítems de mascotas.
--
-- Cada ADD COLUMN es condicional porque las bases de desarrollo que corrieron con
-- ddl-auto: update pueden tener ya las columnas, y MySQL no soporta ADD COLUMN IF NOT
-- EXISTS (mismo patrón que V10).

-- ─── 1. Liquidación de la emisión de llaves en sesiones de juego ────────────────
-- coins_earned ya guarda lo que se le cobró a la campaña (lo financiado). Faltaba lo
-- acreditado al jugador (financiado × multiplicador de nivel) y la marca de si el
-- diferencial ya se liquidó en tesorería. Igual que ad_likes y survey_rewards en V10:
-- credited_amount nullable para que el historial nunca entre a la liquidación.

-- game_sessions.credited_amount
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'game_sessions' AND column_name = 'credited_amount'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE game_sessions ADD COLUMN credited_amount bigint DEFAULT NULL AFTER coins_earned',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- game_sessions.issuance_settled
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'game_sessions' AND column_name = 'issuance_settled'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE game_sessions ADD COLUMN issuance_settled bit(1) NOT NULL DEFAULT b''0'' AFTER credited_amount',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── 2. Bolsa de la solicitud de integración al catálogo de mascotas ────────────
-- El comercial reserva un presupuesto al enviar la solicitud; cada compra del ítem
-- publicado descuenta de ahí el cobro por uso. budget_cents nullable: las solicitudes
-- anteriores no reservaron nada y sus ítems no cobran.

-- catalog_integration_requests.budget_cents
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'catalog_integration_requests' AND column_name = 'budget_cents'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE catalog_integration_requests ADD COLUMN budget_cents bigint DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- catalog_integration_requests.spent_cents
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'catalog_integration_requests' AND column_name = 'spent_cents'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE catalog_integration_requests ADD COLUMN spent_cents bigint NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── 3. Enums nuevos ─────────────────────────────────────────────────────────────
-- Sin el valor en el ENUM, validate no se queja pero el INSERT falla en tiempo de
-- ejecución ("Data truncated for column"). MODIFY es idempotente: redefine la lista.

-- BudgetTransaction.TransactionType ganó PET_ITEM_REQUEST (la reserva de la bolsa).
ALTER TABLE budget_transactions MODIFY COLUMN type
    ENUM('AD_VIEW','BRANDING_REQUEST','GAME_REWARD','MANUAL_ADJUSTMENT','PET_ITEM_REQUEST') NOT NULL;

-- MovementConcept ganó PET_ITEM_CHARGE_TO_OPERATIONS (el cobro por uso). Es la lista
-- de V10 más el valor nuevo.
ALTER TABLE treasury_movements MODIFY COLUMN concept
    ENUM('BASIC_PLAN_SUBSCRIPTION','BASIC_PLAN_SUBSCRIPTION_VAT','BUSINESS_DEPOSIT_FORTIFICATION',
         'BUSINESS_DEPOSIT_KEYS','BUSINESS_DEPOSIT_OPERATIONS','BUSINESS_DEPOSIT_VAT',
         'COMMISSION_RETENTION','COMMISSION_REVERSAL','COMMISSION_VAT_RETENTION','COMMISSION_VAT_REVERSAL',
         'COPAYMENT_KEYS_CONVERSION','EXPIRED_KEYS_TO_FORTIFICATION','FORTIFICATION_PURCHASE',
         'KEYS_ISSUANCE_DEFICIT_FUNDING','KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS',
         'PAYOUT_TO_BUSINESS','PET_GAME_KEYS_TO_OPERATIONS','PET_ITEM_CHARGE_TO_OPERATIONS',
         'REFUND_CASH_TO_OPERATIONS','REFUND_KEYS_TO_RESERVE','REFUND_TO_BUYER',
         'SALE_TO_PAYOUT_PENDING') NOT NULL;
