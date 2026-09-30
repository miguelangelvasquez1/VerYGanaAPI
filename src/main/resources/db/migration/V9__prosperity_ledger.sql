-- MP-05: Motor de Prosperidad (Contrato B 2.31-2.32, 5.7-5.8, 10.1-10.6, 10.20-10.27).
-- Solo los comerciales STANDARD (Tipo B) generan Umbral de Prosperidad: cada Investment
-- confirmado genera neto × multiplicador (feature PROSPERITY_THRESHOLD_MULTIPLIER = 4) y
-- ese Umbral se suma al Saldo. El Saldo absorbe el VALOR de las ventas (no la comisión)
-- y la comisión se cobra solo sobre la porción no absorbida.
-- Ver ProsperityServiceImpl.

CREATE TABLE prosperity_accounts (
  id                           BIGINT NOT NULL AUTO_INCREMENT,
  commercial_id                BIGINT NOT NULL,
  balance_cents                BIGINT NOT NULL DEFAULT 0,
  accumulated_threshold_cents  BIGINT NOT NULL DEFAULT 0,
  total_absorbed_cents         BIGINT NOT NULL DEFAULT 0,
  total_reintegrated_cents     BIGINT NOT NULL DEFAULT 0,
  last_sequence                BIGINT NOT NULL DEFAULT 0,
  version                      BIGINT NOT NULL,
  created_at                   DATETIME(6) NOT NULL,
  last_updated                 DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prosperity_account_commercial (commercial_id),
  CONSTRAINT fk_prosperity_account_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prosperity_thresholds (
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  account_id            BIGINT NOT NULL,
  investment_id         BIGINT NOT NULL,
  investment_net_cents  BIGINT NOT NULL,
  multiplier            INT NOT NULL,
  generated_cents       BIGINT NOT NULL,
  plan_id               BIGINT NOT NULL,
  plan_version          INT NOT NULL,
  validated_at          DATETIME(6) NOT NULL,
  created_at            DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prosperity_threshold_investment (investment_id),
  KEY idx_prosperity_threshold_account (account_id),
  CONSTRAINT fk_prosperity_threshold_account FOREIGN KEY (account_id) REFERENCES prosperity_accounts (id),
  CONSTRAINT fk_prosperity_threshold_investment FOREIGN KEY (investment_id) REFERENCES investments (id),
  CONSTRAINT fk_prosperity_threshold_plan FOREIGN KEY (plan_id) REFERENCES plans (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE prosperity_ledger_entries (
  id                    BIGINT NOT NULL AUTO_INCREMENT,
  account_id            BIGINT NOT NULL,
  seq_no                BIGINT NOT NULL,
  type                  ENUM('ADJUSTMENT_CREDIT','ADJUSTMENT_DEBIT','REFUND_REINTEGRATION','SALE_ABSORPTION',
                             'THRESHOLD_GENERATED','THRESHOLD_REVERSAL') NOT NULL,
  amount_cents          BIGINT NOT NULL,
  balance_before_cents  BIGINT NOT NULL,
  balance_after_cents   BIGINT NOT NULL,
  origin_type           ENUM('ADMIN','INVESTMENT','PURCHASE_ITEM') NOT NULL,
  origin_id             VARCHAR(64) NOT NULL,
  threshold_id          BIGINT NULL,
  related_entry_id      BIGINT NULL,
  sale_amount_cents     BIGINT NULL,
  uncovered_cents       BIGINT NULL,
  idempotency_key       VARCHAR(120) NOT NULL,
  effective_at          DATETIME(6) NOT NULL,
  cause                 VARCHAR(500) NULL,
  support_ref           VARCHAR(500) NULL,
  performed_by          VARCHAR(120) NOT NULL,
  created_at            DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_prosperity_entry_idempotency (idempotency_key),
  UNIQUE KEY uk_prosperity_entry_sequence (account_id, seq_no),
  KEY idx_prosperity_entry_origin (origin_type, origin_id),
  CONSTRAINT fk_prosperity_entry_account FOREIGN KEY (account_id) REFERENCES prosperity_accounts (id),
  CONSTRAINT fk_prosperity_entry_threshold FOREIGN KEY (threshold_id) REFERENCES prosperity_thresholds (id),
  CONSTRAINT fk_prosperity_entry_related FOREIGN KEY (related_entry_id) REFERENCES prosperity_ledger_entries (id),
  CONSTRAINT chk_prosperity_entry_amount CHECK (amount_cents > 0 OR (type = 'THRESHOLD_REVERSAL' AND amount_cents = 0)),
  CONSTRAINT chk_prosperity_entry_balance CHECK (balance_after_cents >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Libro append-only (Contrato 1.17, 10.5, 10.27): las correcciones son asientos
-- compensatorios, nunca UPDATE/DELETE. La entidad JPA ya es @Immutable; esto lo
-- garantiza también frente a SQL manual.
CREATE TRIGGER trg_prosperity_ledger_no_update BEFORE UPDATE ON prosperity_ledger_entries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'prosperity_ledger_entries es append-only: registre un asiento compensatorio';

CREATE TRIGGER trg_prosperity_ledger_no_delete BEFORE DELETE ON prosperity_ledger_entries
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'prosperity_ledger_entries es append-only: registre un asiento compensatorio';

CREATE TRIGGER trg_prosperity_threshold_no_update BEFORE UPDATE ON prosperity_thresholds
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'prosperity_thresholds es inmutable: use THRESHOLD_REVERSAL en el libro';

CREATE TRIGGER trg_prosperity_threshold_no_delete BEFORE DELETE ON prosperity_thresholds
FOR EACH ROW SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'prosperity_thresholds es inmutable: use THRESHOLD_REVERSAL en el libro';

-- Porción de cada ítem absorbida por el Saldo de Prosperidad y base sobre la que se
-- calculó la comisión definitiva (NULL para ítems anteriores a MP-05: base = subtotal).
ALTER TABLE purchase_items ADD COLUMN prosperity_absorbed_cents BIGINT NOT NULL DEFAULT 0;
ALTER TABLE purchase_items ADD COLUMN commission_base_cents BIGINT NULL;
ALTER TABLE purchases ADD COLUMN prosperity_absorbed_cents BIGINT NOT NULL DEFAULT 0;

-- PlanDataInitializer solo siembra con la tabla plans vacía (ver V8): los entornos ya
-- sembrados reciben la feature aquí. Solo STANDARD la tiene; BASIC/PREMIUM caen al
-- default 0 = sin Umbral.
INSERT INTO features (code, name, type)
SELECT 'PROSPERITY_THRESHOLD_MULTIPLIER', 'Multiplicador de umbral de prosperidad', 'LIMIT'
WHERE NOT EXISTS (SELECT 1 FROM features WHERE code = 'PROSPERITY_THRESHOLD_MULTIPLIER');

INSERT INTO plan_features (plan_id, feature_id, int_value)
SELECT p.id, f.id, 4
FROM plans p
JOIN features f ON f.code = 'PROSPERITY_THRESHOLD_MULTIPLIER'
WHERE p.code = 'STANDARD'
  AND NOT EXISTS (SELECT 1 FROM plan_features pf WHERE pf.plan_id = p.id AND pf.feature_id = f.id);
