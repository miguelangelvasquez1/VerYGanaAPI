-- Contrato B 4.7 / 4.8: requerimientos de actualización, aclaraciones, incidencias
-- técnicas, hallazgos con medida contractual y alertas para compliance.
--
-- Un requerimiento pide algo concreto, con motivo y regla; su cierre sale de una revisión.
-- Solo un hallazgo con fundamento contractual cambia el estado de la cuenta. Las reglas de
-- alerta se crean vacías: sus umbrales los fija compliance.
--
-- Idempotente: todas las tablas con IF NOT EXISTS.

CREATE TABLE IF NOT EXISTS information_requests (
  id                     BIGINT NOT NULL AUTO_INCREMENT,
  public_id              BINARY(16) NOT NULL,
  commercial_id          BIGINT NOT NULL,
  trigger_event          ENUM('ACTIVITY_CHANGE','DOCUMENT_EXPIRED','ESTABLISHMENT_CHANGE','EXPRESS_REFUSAL','INCIDENT',
                              'INCONSISTENCY','MATERIAL_OPERATION_INCREASE','MISSING_MANIFESTATION','NEW_REQUIREMENT',
                              'RISK') NOT NULL,
  rule_code              VARCHAR(50) NULL,
  rule_version           INT NULL,
  reason                 VARCHAR(1000) NOT NULL,
  relation_to_operation  VARCHAR(1000) NOT NULL,
  status                 ENUM('CANCELLED','CLOSED_SATISFIED','CLOSED_WITH_FINDING','OBSERVED','OPEN','RESPONDED') NOT NULL,
  due_date               DATE NULL,
  issued_by_user_id      BIGINT NULL,
  issued_at              DATETIME(6) NOT NULL,
  dedup_key              VARCHAR(150) NOT NULL,
  open_dedup_key         VARCHAR(150) NULL,
  closed_at              DATETIME(6) NULL,
  closed_by_user_id      BIGINT NULL,
  closing_notes          VARCHAR(1000) NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_info_request_public_id (public_id),
  UNIQUE KEY uk_info_request_open_dedup (open_dedup_key),
  KEY idx_info_request_commercial_status (commercial_id, status),
  KEY idx_info_request_dedup (dedup_key),
  CONSTRAINT fk_info_request_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS information_request_items (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  request_id           BIGINT NOT NULL,
  item_type            ENUM('CLARIFICATION','DOCUMENT','FIELD') NOT NULL,
  document_type        ENUM('CAMARA_COMERCIO','CEDULA_REPRESENTANTE','CERTIFICACION_BANCARIA','MARCA_REGISTRADA','OTRO',
                            'PERMISO_SECTORIAL','RUT') NULL,
  field_path           VARCHAR(100) NULL,
  establishment_id     BIGINT NULL,
  description          VARCHAR(500) NOT NULL,
  acceptable_response  VARCHAR(500) NULL,
  linked_document_id   BIGINT NULL,
  PRIMARY KEY (id),
  KEY idx_info_request_items_request (request_id),
  CONSTRAINT fk_info_request_items_request FOREIGN KEY (request_id) REFERENCES information_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS information_request_communications (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  request_id         BIGINT NOT NULL,
  channel            ENUM('EMAIL','IN_APP') NOT NULL,
  recipient_user_id  BIGINT NOT NULL,
  sent_at            DATETIME(6) NOT NULL,
  delivery_status    ENUM('FAILED','SENT') NOT NULL,
  error_detail       VARCHAR(500) NULL,
  PRIMARY KEY (id),
  KEY idx_info_request_comms_request (request_id),
  CONSTRAINT fk_info_request_comms_request FOREIGN KEY (request_id) REFERENCES information_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS information_request_responses (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  request_id         BIGINT NOT NULL,
  responder_user_id  BIGINT NOT NULL,
  message            VARCHAR(2000) NULL,
  received_at        DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_info_request_responses_request (request_id),
  CONSTRAINT fk_info_request_responses_request FOREIGN KEY (request_id) REFERENCES information_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS information_request_response_documents (
  response_id  BIGINT NOT NULL,
  document_id  BIGINT NOT NULL,
  PRIMARY KEY (response_id, document_id),
  CONSTRAINT fk_info_response_docs_response FOREIGN KEY (response_id) REFERENCES information_request_responses (id),
  CONSTRAINT fk_info_response_docs_document FOREIGN KEY (document_id) REFERENCES commercial_documents (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS information_request_reviews (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  request_id        BIGINT NOT NULL,
  reviewer_user_id  BIGINT NOT NULL,
  result            ENUM('INSUFFICIENT','SUFFICIENT') NOT NULL,
  notes             VARCHAR(1000) NULL,
  reviewed_at       DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_info_request_reviews_request (request_id),
  CONSTRAINT fk_info_request_reviews_request FOREIGN KEY (request_id) REFERENCES information_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS technical_incidents (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  commercial_id        BIGINT NOT NULL,
  context              VARCHAR(100) NOT NULL,
  description          VARCHAR(1000) NOT NULL,
  request_id           BIGINT NULL,
  manifestation_id     BIGINT NULL,
  reported_by_user_id  BIGINT NULL,
  occurred_at          DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_technical_incidents_commercial (commercial_id),
  CONSTRAINT fk_technical_incidents_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id),
  CONSTRAINT fk_technical_incidents_request FOREIGN KEY (request_id) REFERENCES information_requests (id),
  CONSTRAINT fk_technical_incidents_manifestation FOREIGN KEY (manifestation_id) REFERENCES declaration_manifestations (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS compliance_findings (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  public_id               BINARY(16) NOT NULL,
  commercial_id           BIGINT NOT NULL,
  request_id              BIGINT NULL,
  fact_observed           VARCHAR(2000) NOT NULL,
  evidence                VARCHAR(2000) NULL,
  classification          ENUM('EXPRESS_REFUSAL','ISOLATED_INCONSISTENCY','LACK_OF_UPDATE','SUBSANABLE_ERROR',
                               'VERIFIED_FALSE_INFO') NOT NULL,
  severity                ENUM('HIGH','LOW','MEDIUM') NOT NULL,
  impact                  VARCHAR(1000) NOT NULL,
  remediable              BIT(1) NOT NULL,
  contractual_basis       VARCHAR(1000) NOT NULL,
  measure                 ENUM('NONE','PREVENTIVE_RESTRICTION','SUSPENSION','TERMINATION_REFERRAL','WARNING') NOT NULL,
  measure_scope           VARCHAR(500) NULL,
  account_status_applied  ENUM('ACTIVE','PENDING_ACCEPTANCE','PENDING_ACTIVATION','PENDING_VERIFICATION',
                               'PREVENTIVELY_RESTRICTED','REGISTRATION_STARTED','SUSPENDED','TERMINATED') NULL,
  decided_by_user_id      BIGINT NOT NULL,
  decided_at              DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_compliance_findings_public_id (public_id),
  KEY idx_compliance_findings_commercial (commercial_id),
  CONSTRAINT fk_compliance_findings_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id),
  CONSTRAINT fk_compliance_findings_request FOREIGN KEY (request_id) REFERENCES information_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS compliance_alert_rules (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  code                VARCHAR(50) NOT NULL,
  version             INT NOT NULL,
  rule_type           ENUM('PLATFORM_SALES_GROWTH') NOT NULL,
  description         VARCHAR(500) NOT NULL,
  legal_basis         VARCHAR(500) NOT NULL,
  params_json         VARCHAR(2000) NOT NULL,
  active              BIT(1) NOT NULL,
  created_by_user_id  BIGINT NOT NULL,
  created_at          DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_alert_rule_code_version (code, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS compliance_alerts (
  id                   BIGINT NOT NULL AUTO_INCREMENT,
  rule_id              BIGINT NULL,
  trigger_event        ENUM('ACTIVITY_CHANGE','DOCUMENT_EXPIRED','ESTABLISHMENT_CHANGE','EXPRESS_REFUSAL','INCIDENT',
                            'INCONSISTENCY','MATERIAL_OPERATION_INCREASE','MISSING_MANIFESTATION','NEW_REQUIREMENT',
                            'RISK') NOT NULL,
  commercial_id        BIGINT NOT NULL,
  detected_at          DATETIME(6) NOT NULL,
  data_used_json       VARCHAR(2000) NOT NULL,
  message              VARCHAR(1000) NOT NULL,
  reviewed_at          DATETIME(6) NULL,
  reviewed_by_user_id  BIGINT NULL,
  review_notes         VARCHAR(1000) NULL,
  request_id           BIGINT NULL,
  PRIMARY KEY (id),
  KEY idx_compliance_alert_open (reviewed_at, detected_at),
  KEY idx_compliance_alert_commercial (commercial_id),
  CONSTRAINT fk_compliance_alert_rule FOREIGN KEY (rule_id) REFERENCES compliance_alert_rules (id),
  CONSTRAINT fk_compliance_alert_commercial FOREIGN KEY (commercial_id) REFERENCES commercial_details (user_id),
  CONSTRAINT fk_compliance_alert_request FOREIGN KEY (request_id) REFERENCES information_requests (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
