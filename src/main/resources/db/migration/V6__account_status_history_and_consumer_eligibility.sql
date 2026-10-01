CREATE TABLE account_status_history (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  from_status ENUM('REGISTRATION_STARTED','PENDING_ACCEPTANCE','PENDING_VERIFICATION',
                    'PENDING_ACTIVATION','ACTIVE','PREVENTIVELY_RESTRICTED','SUSPENDED','TERMINATED') NOT NULL,
  to_status ENUM('REGISTRATION_STARTED','PENDING_ACCEPTANCE','PENDING_VERIFICATION',
                 'PENDING_ACTIVATION','ACTIVE','PREVENTIVELY_RESTRICTED','SUSPENDED','TERMINATED') NOT NULL,
  reason VARCHAR(255) NOT NULL,
  actor VARCHAR(255) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ash_user_id (user_id),
  KEY idx_ash_user_id_to_status (user_id, to_status, occurred_at),
  CONSTRAINT fk_ash_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ============================================================
-- Eligibility Engine (MP-38): aceptación de términos y declaración
-- explícita de mayoría de edad en el registro de consumidor.
-- ============================================================
ALTER TABLE consumer_details ADD COLUMN terms_version VARCHAR(20) NULL;
ALTER TABLE consumer_details ADD COLUMN terms_accepted_at DATETIME(6) NULL;
ALTER TABLE consumer_details ADD COLUMN age_declared_at DATETIME(6) NULL;

-- Backfill de filas existentes (seeds / registros previos a este cambio):
-- no hay versión de términos real que atribuirles, se marca como 'LEGACY'
-- y se usa su propia fecha de registro como aproximación razonable.
UPDATE consumer_details cd
JOIN users u ON cd.user_id = u.id
SET cd.terms_version = 'LEGACY',
    cd.terms_accepted_at = u.registered_date,
    cd.age_declared_at = u.registered_date
WHERE cd.terms_version IS NULL;

ALTER TABLE consumer_details MODIFY COLUMN terms_version VARCHAR(20) NOT NULL;
ALTER TABLE consumer_details MODIFY COLUMN terms_accepted_at DATETIME(6) NOT NULL;
ALTER TABLE consumer_details MODIFY COLUMN age_declared_at DATETIME(6) NOT NULL;
