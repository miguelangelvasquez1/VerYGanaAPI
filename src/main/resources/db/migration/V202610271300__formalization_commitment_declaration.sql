-- Contrato B 1.7 / 4.7: declaración de compromiso de crecimiento y formalización.
--
-- 1. declaration_texts: texto literal versionado e inmutable, con su huella SHA-256.
-- 2. declaration_disclosures: cuándo se puso el texto a disposición (consultar no acepta).
-- 3. declaration_manifestations: lo que pasó con la casilla no premarcada del apartado 9 de
--    la Hoja Final al aprobar el contrato (manifestación, ausencia, negativa expresa o falla
--    técnica), con actor, contrato y versión. Una manifestación por proceso contractual.
--
-- Idempotente: tablas con IF NOT EXISTS y la semilla se salta si la versión ya existe.

CREATE TABLE IF NOT EXISTS declaration_texts (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  code          ENUM('FORMALIZATION_COMMITMENT') NOT NULL,
  version       VARCHAR(20) NOT NULL,
  full_text     VARCHAR(4000) NOT NULL,
  text_hash     VARCHAR(64) NOT NULL,
  published_at  DATETIME(6) NOT NULL,
  active        BIT(1) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_declaration_text_version (code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS declaration_disclosures (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  commercial_id        BIGINT NOT NULL,
  declaration_text_id  BIGINT NOT NULL,
  context              ENUM('CONTRACT_SHEET','TAX_PROFILE') NOT NULL,
  disclosed_at         DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_declaration_disclosure_commercial (commercial_id),
  CONSTRAINT fk_declaration_disclosure_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id),
  CONSTRAINT fk_declaration_disclosure_text FOREIGN KEY (declaration_text_id) REFERENCES declaration_texts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS declaration_manifestations (
  id                          BIGINT NOT NULL AUTO_INCREMENT,
  commercial_id               BIGINT NOT NULL,
  code                        ENUM('FORMALIZATION_COMMITMENT') NOT NULL,
  declaration_text_id         BIGINT NOT NULL,
  contract_id                 BIGINT NOT NULL,
  contract_version            INT NOT NULL,
  status                      ENUM('EXPRESS_REFUSAL','MANIFESTED','NOT_MANIFESTED','TECHNICAL_INCIDENT') NOT NULL,
  detail                      VARCHAR(1000) NULL,
  actor_user_id               BIGINT NOT NULL,
  actor_legal_rep_name        VARCHAR(200) NULL,
  actor_legal_rep_doc_type    ENUM('CC','CE','PP') NULL,
  actor_legal_rep_doc_number  VARCHAR(20) NULL,
  occurred_at                 DATETIME(6) NOT NULL,
  ip_address                  VARCHAR(64) NULL,
  user_agent                  VARCHAR(500) NULL,
  idempotency_key             VARCHAR(120) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_manifestation_idempotency_key (idempotency_key),
  KEY idx_manifestation_commercial_code (commercial_id, code),
  KEY idx_manifestation_contract (contract_id),
  CONSTRAINT fk_manifestation_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id),
  CONSTRAINT fk_manifestation_text FOREIGN KEY (declaration_text_id) REFERENCES declaration_texts (id),
  CONSTRAINT fk_manifestation_contract FOREIGN KEY (contract_id) REFERENCES commercial_contracts (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Texto literal del numeral 4.7 (no se resume ni se altera "cuando legalmente corresponda").
SET @commitment := 'Reconozco que mi participación en VERYGANA puede aumentar mi exposición comercial y el volumen de mis operaciones, sin garantía de resultados. Me comprometo a fortalecer razonablemente mi capacidad operativa y a actualizar el RUT, cumplir las obligaciones de facturación, inscribirme como responsable del IVA y atender los demás deberes contables y tributarios cuando legalmente corresponda.';

INSERT INTO declaration_texts (code, version, full_text, text_hash, published_at, active)
SELECT 'FORMALIZATION_COMMITMENT', '1', @commitment, SHA2(@commitment, 256), NOW(6), 1
WHERE NOT EXISTS (
    SELECT 1 FROM declaration_texts WHERE code = 'FORMALIZATION_COMMITMENT' AND version = '1'
);
