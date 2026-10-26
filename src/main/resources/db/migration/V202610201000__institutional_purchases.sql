-- Contrato B 10.16 y 5.9: compras de VERYGANA para reconocimientos.
--
-- 1. purchases distingue al comprador (CONSUMER o VERYGANA) del beneficiario. En una
--    compra de VERYGANA consumer_id queda en NULL y la persona que recibe va en
--    beneficiary_id: el beneficiario nunca aparece como comprador.
-- 2. origin_key distingue un reintento de una compra nueva (Idempotency-Key del admin).
-- 3. purchase_items.computable_status: la Venta Computable se reconoce una sola vez,
--    cuando los componentes que financian el precio suman exactamente el subtotal.
-- 4. sale_components (5.9): de qué está hecho el precio de cada ítem (dinero del usuario,
--    respaldo de llaves, pago de VERYGANA) con su referencia de origen.
-- 5. recognitions: el reconocimiento que origina la compra, con el criterio por el que el
--    admin eligió al beneficiario.
-- 6. Tesorería: FORTIFICATION_PURCHASE ya existía; se agrega su reverso.
--
-- Idempotente: cada bloque se salta si ya se aplicó.

-- ── purchases ───────────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'purchases' AND column_name = 'buyer_type'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchases
        ADD COLUMN buyer_type ENUM(''CONSUMER'',''VERYGANA'') NOT NULL DEFAULT ''CONSUMER'',
        ADD COLUMN beneficiary_id BIGINT NULL,
        ADD COLUMN origin_key VARCHAR(120) NULL,
        ADD UNIQUE KEY uk_purchases_origin_key (origin_key),
        ADD KEY idx_purchases_beneficiary (beneficiary_id),
        ADD KEY idx_purchases_buyer_type (buyer_type),
        ADD CONSTRAINT fk_purchases_beneficiary FOREIGN KEY (beneficiary_id)
            REFERENCES consumer_details (user_id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE purchases MODIFY COLUMN consumer_id BIGINT NULL;

-- Comprador y beneficiario coherentes con el tipo de compra.
SET @chk_exists := (
    SELECT COUNT(*) FROM information_schema.table_constraints
    WHERE table_schema = DATABASE() AND table_name = 'purchases'
      AND constraint_name = 'chk_purchases_buyer'
);
SET @ddl := IF(@chk_exists = 0,
    'ALTER TABLE purchases ADD CONSTRAINT chk_purchases_buyer CHECK (
        (buyer_type = ''CONSUMER'' AND consumer_id IS NOT NULL AND beneficiary_id IS NULL)
        OR (buyer_type = ''VERYGANA'' AND consumer_id IS NULL AND beneficiary_id IS NOT NULL))',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── purchase_items ──────────────────────────────────────────────────────────
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'purchase_items' AND column_name = 'computable_status'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchase_items
        ADD COLUMN computable_status ENUM(''PENDING'',''RECOGNIZED'',''REVERSED'') NOT NULL DEFAULT ''PENDING'',
        ADD COLUMN recognized_at DATETIME(6) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── sale_components ─────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sale_components (
  id                       BIGINT NOT NULL AUTO_INCREMENT,
  purchase_item_id         BIGINT NOT NULL,
  component_type           ENUM('KEYS_BACKING','USER_CASH','VERYGANA_PAYMENT') NOT NULL,
  payer_type               ENUM('CONSUMER','VERYGANA') NOT NULL,
  source_ref               VARCHAR(64) NOT NULL,
  amount_cents             BIGINT NOT NULL,
  status                   ENUM('RECOGNIZED','REVERSED') NOT NULL,
  reverses_component_id    BIGINT NULL,
  cause                    VARCHAR(500) NULL,
  created_at               DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  -- Un componente se reconoce una vez y se reversa una vez: un reintento no lo duplica.
  UNIQUE KEY uk_sale_components_item_type_status (purchase_item_id, component_type, status),
  KEY idx_sale_components_source_ref (source_ref),
  CONSTRAINT fk_sale_components_item FOREIGN KEY (purchase_item_id) REFERENCES purchase_items (id),
  CONSTRAINT fk_sale_components_reverses FOREIGN KEY (reverses_component_id) REFERENCES sale_components (id),
  CONSTRAINT chk_sale_components_amount CHECK (amount_cents > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── recognitions ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS recognitions (
  id                       BIGINT NOT NULL AUTO_INCREMENT,
  public_id                BINARY(16) NOT NULL,
  purchase_id              BIGINT NOT NULL,
  funding_ref              BINARY(16) NOT NULL,
  selection_criterion      ENUM('GAMES_PLAYED','KEYS_EARNED','OTHER','PURCHASES_AMOUNT','PURCHASES_COUNT','XP') NOT NULL,
  selection_metric_value   BIGINT NULL,
  selection_period_from    DATETIME(6) NULL,
  selection_period_to      DATETIME(6) NULL,
  reason                   VARCHAR(500) NOT NULL,
  created_by_admin_id      BIGINT NOT NULL,
  status                   ENUM('CANCELLED','DELIVERED','EXPIRED','PURCHASED') NOT NULL,
  cancel_cause             VARCHAR(500) NULL,
  cancelled_by_admin_id    BIGINT NULL,
  created_at               DATETIME(6) NOT NULL,
  delivered_at             DATETIME(6) NULL,
  closed_at                DATETIME(6) NULL,
  version                  BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_recognitions_public_id (public_id),
  UNIQUE KEY uk_recognitions_purchase (purchase_id),
  UNIQUE KEY uk_recognitions_funding_ref (funding_ref),
  KEY idx_recognitions_status_created (status, created_at),
  CONSTRAINT fk_recognitions_purchase FOREIGN KEY (purchase_id) REFERENCES purchases (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── Índices para elegir candidatos ──────────────────────────────────────────
SET @idx_exists := (
    SELECT COUNT(*) FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'key_transactions'
      AND index_name = 'idx_key_tx_type_created'
);
SET @ddl := IF(@idx_exists = 0,
    'ALTER TABLE key_transactions ADD KEY idx_key_tx_type_created (type, created_at)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── Tesorería ───────────────────────────────────────────────────────────────
ALTER TABLE treasury_movements MODIFY COLUMN concept
    ENUM('BASIC_PLAN_SUBSCRIPTION','BASIC_PLAN_SUBSCRIPTION_VAT','BUSINESS_DEPOSIT_FORTIFICATION',
         'BUSINESS_DEPOSIT_KEYS','BUSINESS_DEPOSIT_OPERATIONS','BUSINESS_DEPOSIT_VAT',
         'COMMISSION_RETENTION','COMMISSION_REVERSAL','COMMISSION_VAT_RETENTION','COMMISSION_VAT_REVERSAL',
         'COPAYMENT_KEYS_CONVERSION','EXPIRED_KEYS_TO_FORTIFICATION','FORTIFICATION_PURCHASE',
         'FORTIFICATION_PURCHASE_REVERSAL',
         'KEYS_ISSUANCE_DEFICIT_FUNDING','KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS',
         'PAYOUT_TO_BUSINESS','PET_GAME_KEYS_TO_OPERATIONS','PET_ITEM_CHARGE_TO_OPERATIONS',
         'PROMOTION_EXCESS_REFUND',
         'PROMOTION_GUARANTEE_FROM_FORTIFICATION','PROMOTION_GUARANTEE_RETURN_TO_FORTIFICATION',
         'PROMOTION_GUARANTEE_TO_KEYS_RESERVE',
         'REFUND_CASH_TO_OPERATIONS','REFUND_KEYS_TO_RESERVE','REFUND_TO_BUYER',
         'SALE_TO_PAYOUT_PENDING') NOT NULL;
