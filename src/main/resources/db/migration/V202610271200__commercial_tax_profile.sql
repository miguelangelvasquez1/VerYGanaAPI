-- Contrato B 4.7 / 4.8: expediente documental y ficha tributaria del empresario.
--
-- 1. commercial_onboarding.current_step: paso TAX_PROFILE_PENDING (ficha tributaria),
--    entre la carga documental y el contrato.
-- 2. commercial_documents: el documento pasa a vivir en el expediente del empresario,
--    con historial (SUPERSEDED en lugar de borrarse), vigencia, titular y revisión de
--    contenido por compliance.
-- 3. Ficha tributaria (incisos a–h): cabecera, versiones inmutables con sus actividades e
--    ingresos, revisiones por sección, establecimientos y catálogo de códigos tributarios.
--
-- Sin backfill: los documentos anteriores quedan sin commercial_id ni revisión (datos de
-- desarrollo). Idempotente: cada bloque se salta si ya se aplicó.

-- ── 1. Paso nuevo del registro ──────────────────────────────────────────────
ALTER TABLE commercial_onboarding MODIFY COLUMN current_step
    ENUM('ADVISOR_CONTACT_PENDING','BUSINESS_REVIEW_PENDING','CLASSIFICATION_PENDING','COMPLETED',
         'CONTRACT_PENDING','DIAGNOSTIC_PENDING','DOCUMENTS_PENDING','LEGAL_IDENTIFICATION_PENDING',
         'PAYMENT_PENDING','PLAN_PENDING','SIGNATURE_PENDING','TAX_PROFILE_PENDING','TERMS_PENDING',
         'VERYGANA_REVIEW_PENDING') NOT NULL;

-- ── 2. Expediente documental ────────────────────────────────────────────────
ALTER TABLE commercial_documents MODIFY COLUMN status
    ENUM('ORPHANED','PENDING','SUPERSEDED','VALIDATED') NOT NULL;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'commercial_documents' AND column_name = 'commercial_id'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE commercial_documents
        ADD COLUMN commercial_id BIGINT NULL,
        ADD COLUMN replaces_document_id BIGINT NULL,
        ADD COLUMN superseded_at DATETIME(6) NULL,
        ADD COLUMN issued_at DATE NULL,
        ADD COLUMN expires_at DATE NULL,
        ADD COLUMN holder_identification VARCHAR(30) NULL,
        ADD COLUMN review_status ENUM(''ACCEPTED'',''NOT_REVIEWED'',''OBSERVED'',''REJECTED'') NULL,
        ADD COLUMN reviewed_by_user_id BIGINT NULL,
        ADD COLUMN reviewed_at DATETIME(6) NULL,
        ADD COLUMN review_notes VARCHAR(1000) NULL,
        ADD KEY idx_commercial_documents_commercial (commercial_id, status),
        ADD KEY idx_commercial_documents_expires (expires_at),
        ADD CONSTRAINT fk_commercial_documents_commercial FOREIGN KEY (commercial_id)
            REFERENCES commercial_details (user_id),
        ADD CONSTRAINT fk_commercial_documents_replaces FOREIGN KEY (replaces_document_id)
            REFERENCES commercial_documents (id)',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ── 3. Catálogo de códigos tributarios ──────────────────────────────────────
-- Se crea vacío: su contenido lo carga compliance con fundamento y vigencia.
CREATE TABLE IF NOT EXISTS tax_catalog_entries (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  catalog_type         ENUM('INC_CONDITION','INVOICING_METHOD','REGIME','RUT_RESPONSIBILITY','VAT_CONDITION') NOT NULL,
  code                 VARCHAR(30) NOT NULL,
  label                VARCHAR(200) NOT NULL,
  legal_basis          VARCHAR(500) NOT NULL,
  valid_from           DATE NOT NULL,
  valid_to             DATE NULL,
  created_by_user_id   BIGINT NOT NULL,
  created_at           DATETIME(6) NOT NULL,
  retired_by_user_id   BIGINT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tax_catalog_type_code (catalog_type, code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── 4. Ficha tributaria ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS tax_profiles (
  id               BIGINT NOT NULL AUTO_INCREMENT,
  commercial_id    BIGINT NOT NULL,
  status           ENUM('OBSERVED','REVIEWED','SUBMITTED') NOT NULL,
  current_version  INT NOT NULL,
  created_at       DATETIME(6) NOT NULL,
  updated_at       DATETIME(6) NOT NULL,
  lock_version     BIGINT NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tax_profiles_commercial (commercial_id),
  KEY idx_tax_profiles_status (status),
  CONSTRAINT fk_tax_profiles_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tax_profile_versions (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tax_profile_id          BIGINT NOT NULL,
  version_number          INT NOT NULL,
  rut_document_id         BIGINT NULL,
  regime_code             VARCHAR(30) NULL,
  vat_condition_code      VARCHAR(30) NULL,
  inc_condition_code      VARCHAR(30) NULL,
  tax_condition_scope     VARCHAR(1000) NULL,
  invoicing_obligated     BIT(1) NOT NULL,
  invoicing_method_code   VARCHAR(30) NULL,
  invoicing_notes         VARCHAR(1000) NULL,
  withholding_agent       BIT(1) NOT NULL,
  withholding_notes       VARCHAR(1000) NULL,
  establishments_count    INT NOT NULL,
  current_capacity        VARCHAR(2000) NOT NULL,
  growth_scenario         VARCHAR(2000) NULL,
  growth_measures         VARCHAR(2000) NULL,
  has_accounting_advisor  BIT(1) NOT NULL,
  advisor_scope           VARCHAR(1000) NULL,
  submitted_by_user_id    BIGINT NOT NULL,
  submitted_at            DATETIME(6) NOT NULL,
  submitted_ip            VARCHAR(64) NULL,
  submitted_user_agent    VARCHAR(500) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tax_profile_version (tax_profile_id, version_number),
  CONSTRAINT fk_tax_profile_versions_profile FOREIGN KEY (tax_profile_id) REFERENCES tax_profiles (id),
  CONSTRAINT fk_tax_profile_versions_rut FOREIGN KEY (rut_document_id) REFERENCES commercial_documents (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tax_profile_version_rut_responsibilities (
  tax_profile_version_id  BIGINT NOT NULL,
  position                INT NOT NULL,
  code                    VARCHAR(30) NOT NULL,
  PRIMARY KEY (tax_profile_version_id, position),
  CONSTRAINT fk_tax_rut_resp_version FOREIGN KEY (tax_profile_version_id) REFERENCES tax_profile_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tax_profile_activities (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tax_profile_version_id  BIGINT NOT NULL,
  ciiu_code               VARCHAR(4) NOT NULL,
  description             VARCHAR(300) NULL,
  is_main                 BIT(1) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tax_activities_version (tax_profile_version_id),
  CONSTRAINT fk_tax_activities_version FOREIGN KEY (tax_profile_version_id) REFERENCES tax_profile_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tax_income_reports (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tax_profile_version_id  BIGINT NOT NULL,
  concept                 ENUM('INGRESOS','RECAUDOS','VENTAS') NOT NULL,
  period_start            DATE NOT NULL,
  period_end              DATE NOT NULL,
  amount_cents            BIGINT NOT NULL,
  currency                VARCHAR(3) NOT NULL,
  scope                   ENUM('CONSOLIDATED_INCLUDES_PLATFORM','EXTERNAL_ONLY','PLATFORM_ONLY') NOT NULL,
  support_document_id     BIGINT NULL,
  notes                   VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_tax_income_version (tax_profile_version_id),
  CONSTRAINT fk_tax_income_version FOREIGN KEY (tax_profile_version_id) REFERENCES tax_profile_versions (id),
  CONSTRAINT fk_tax_income_document FOREIGN KEY (support_document_id) REFERENCES commercial_documents (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS tax_profile_section_reviews (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  tax_profile_version_id  BIGINT NOT NULL,
  section                 ENUM('A_RUT','B_REGIME_ACTIVITIES','C_VAT_INC','D_INVOICING_WITHHOLDING','E_INCOME',
                               'F_ESTABLISHMENTS','G_GROWTH_CAPACITY','H_ADVISORY') NOT NULL,
  result                  ENUM('NOT_APPLICABLE','OBSERVED','VERIFIED') NOT NULL,
  notes                   VARCHAR(1000) NULL,
  reviewed_by_user_id     BIGINT NOT NULL,
  reviewed_at             DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_tax_section_review_version (tax_profile_version_id),
  CONSTRAINT fk_tax_section_review_version FOREIGN KEY (tax_profile_version_id) REFERENCES tax_profile_versions (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ── 5. Establecimientos ─────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS commercial_establishments (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  commercial_id       BIGINT NOT NULL,
  name                VARCHAR(150) NOT NULL,
  address             VARCHAR(300) NOT NULL,
  address_normalized  VARCHAR(300) NOT NULL,
  municipality_code   VARCHAR(5) NOT NULL,
  relation            ENUM('FRANCHISE','LEASED','OTHER','OWNED') NOT NULL,
  status              ENUM('ACTIVE','CLOSED') NOT NULL,
  created_at          DATETIME(6) NOT NULL,
  closed_at           DATETIME(6) NULL,
  closed_reason       VARCHAR(300) NULL,
  PRIMARY KEY (id),
  KEY idx_establishment_commercial_status (commercial_id, status),
  CONSTRAINT fk_establishment_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id),
  CONSTRAINT fk_establishment_municipality FOREIGN KEY (municipality_code) REFERENCES municipality (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
