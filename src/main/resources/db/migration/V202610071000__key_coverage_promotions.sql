-- MP-12: porción en llaves por ítem y promociones especiales de cobertura
-- (Contrato B 2.26-2.27, 6.22.8, 7.9-7.10).
--
-- 1. Cada ítem de compra guarda cuánto de su precio se cubrió con llaves, con qué tope
--    y bajo qué promoción. Antes el reparto llaves/dinero solo existía a nivel de copago
--    y un reembolso tenía que prorratearlo.
-- 2. Promoción especial: el admin la autoriza con presupuesto y vigencia, y cada
--    empresario acepta expresamente incorporar su oferta. Sube el tope del 50 % ordinario
--    hasta un máximo del 70 %.
-- 3. PROMOTIONS_RESERVE: al autorizar, el presupuesto se aparta de FORTIFICATION como
--    garantía. Ver KeyCoveragePromotionServiceImpl y TreasuryServiceImpl.
--
-- Idempotente: cada bloque se salta si ya se aplicó.

CREATE TABLE IF NOT EXISTS key_coverage_promotions (
  id                         BIGINT NOT NULL AUTO_INCREMENT,
  name                       VARCHAR(150) NOT NULL,
  coverage_pct               INT NOT NULL,
  starts_at                  DATETIME(6) NOT NULL,
  ends_at                    DATETIME(6) NOT NULL,
  budget_cents               BIGINT NOT NULL,
  reserved_cents             BIGINT NOT NULL DEFAULT 0,
  consumed_cents             BIGINT NOT NULL DEFAULT 0,
  guarantee_drawn_cents      BIGINT NOT NULL DEFAULT 0,
  guarantee_returned_at      DATETIME(6) NULL,
  max_excess_cents_per_user  BIGINT NULL,
  status                     ENUM('AUTHORIZED','DRAFT','ENDED','PAUSED') NOT NULL,
  config_version             INT NOT NULL,
  terms_text                 TEXT NULL,
  authorized_by_user_id      BIGINT NULL,
  authorized_at              DATETIME(6) NULL,
  version                    BIGINT NOT NULL,
  created_at                 DATETIME(6) NOT NULL,
  updated_at                 DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_kcp_status_dates (status, starts_at, ends_at),
  -- El contrato no admite excepción: por encima del ordinario y nunca más del 70 %.
  CONSTRAINT chk_kcp_coverage_pct CHECK (coverage_pct > 50 AND coverage_pct <= 70),
  CONSTRAINT chk_kcp_dates CHECK (ends_at > starts_at),
  CONSTRAINT chk_kcp_budget CHECK (budget_cents > 0 AND reserved_cents >= 0 AND consumed_cents >= 0
                                   AND reserved_cents + consumed_cents <= budget_cents
                                   AND guarantee_drawn_cents >= 0 AND guarantee_drawn_cents <= budget_cents)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS key_coverage_promotion_offers (
  id                             BIGINT NOT NULL AUTO_INCREMENT,
  promotion_id                   BIGINT NOT NULL,
  product_id                     BIGINT NOT NULL,
  commercial_id                  BIGINT NOT NULL,
  status                         ENUM('ACCEPTED','PENDING_ACCEPTANCE','REJECTED','WITHDRAWN') NOT NULL,
  responded_at                   DATETIME(6) NULL,
  accepted_price_cents           BIGINT NULL,
  accepted_coverage_pct          INT NULL,
  accepted_max_coverage_cents    BIGINT NULL,
  accepted_min_user_cash_cents   BIGINT NULL,
  accepted_commission_pct        INT NULL,
  accepted_config_version        INT NULL,
  created_at                     DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_kcpo_promotion_product (promotion_id, product_id),
  KEY idx_kcpo_product (product_id),
  KEY idx_kcpo_commercial (commercial_id),
  CONSTRAINT fk_kcpo_promotion FOREIGN KEY (promotion_id) REFERENCES key_coverage_promotions (id),
  CONSTRAINT fk_kcpo_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── purchase_items: porción en llaves por ítem ──────────────────────────────
-- keys_value_cents queda NULL en los ítems anteriores: ahí el reembolso sigue
-- prorrateando desde el copago, porque el dato por ítem no existe.
SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'purchase_items' AND column_name = 'keys_value_cents'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE purchase_items
        ADD COLUMN keys_value_cents BIGINT NULL,
        ADD COLUMN keys_pct_applied INT NULL,
        ADD COLUMN key_coverage_promotion_id BIGINT NULL,
        ADD COLUMN promotion_excess_cents BIGINT NOT NULL DEFAULT 0,
        ADD KEY idx_pi_key_coverage_promotion (key_coverage_promotion_id),
        ADD CONSTRAINT fk_pi_key_coverage_promotion FOREIGN KEY (key_coverage_promotion_id)
            REFERENCES key_coverage_promotions (id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── Tesorería: bolsillo de garantía y sus conceptos ─────────────────────────
-- La fila de la cuenta la crea TreasuryDataInitializer al arrancar.
ALTER TABLE treasury_accounts MODIFY COLUMN code
    ENUM('CONNECTIVITY','EXTERNAL_INCOME','FORTIFICATION','INFRASTRUCTURE','KEYS_RESERVE',
         'OPERATIONS','PAYOUTS_PENDING','PAYROLL','PROMOTIONS_RESERVE','TAX_RESERVE') NOT NULL;

ALTER TABLE treasury_movements MODIFY COLUMN concept
    ENUM('BASIC_PLAN_SUBSCRIPTION','BASIC_PLAN_SUBSCRIPTION_VAT','BUSINESS_DEPOSIT_FORTIFICATION',
         'BUSINESS_DEPOSIT_KEYS','BUSINESS_DEPOSIT_OPERATIONS','BUSINESS_DEPOSIT_VAT',
         'COMMISSION_RETENTION','COMMISSION_REVERSAL','COMMISSION_VAT_RETENTION','COMMISSION_VAT_REVERSAL',
         'COPAYMENT_KEYS_CONVERSION','EXPIRED_KEYS_TO_FORTIFICATION','FORTIFICATION_PURCHASE',
         'KEYS_ISSUANCE_DEFICIT_FUNDING','KEYS_ISSUANCE_SURPLUS_TO_OPERATIONS',
         'PAYOUT_TO_BUSINESS','PET_GAME_KEYS_TO_OPERATIONS','PET_ITEM_CHARGE_TO_OPERATIONS',
         'PROMOTION_GUARANTEE_FROM_FORTIFICATION','PROMOTION_GUARANTEE_RETURN_TO_FORTIFICATION',
         'PROMOTION_GUARANTEE_TO_KEYS_RESERVE',
         'REFUND_CASH_TO_OPERATIONS','REFUND_KEYS_TO_RESERVE','REFUND_TO_BUYER',
         'SALE_TO_PAYOUT_PENDING') NOT NULL;
