-- Paso 1: renombrar columna y ampliar el enum a un superset (valores viejos + nuevos)
ALTER TABLE users CHANGE COLUMN user_state account_status
  ENUM('ACTIVE','BLOCKED','PENDING_EMAIL','PENDING_KYC_REVIEW',
       'REGISTRATION_STARTED','PENDING_ACCEPTANCE','PENDING_VERIFICATION',
       'PENDING_ACTIVATION','PREVENTIVELY_RESTRICTED','SUSPENDED','TERMINATED')
  NOT NULL;

-- Paso 2: remapear datos existentes
UPDATE users SET account_status = CASE account_status
  WHEN 'PENDING_EMAIL' THEN 'PENDING_VERIFICATION'
  WHEN 'PENDING_KYC_REVIEW' THEN 'PENDING_ACTIVATION'
  WHEN 'BLOCKED' THEN 'SUSPENDED'
  ELSE account_status
END;

-- Paso 3: reducir el enum al set final (ya no quedan filas con los valores viejos)
ALTER TABLE users MODIFY COLUMN account_status
  ENUM('REGISTRATION_STARTED','PENDING_ACCEPTANCE','PENDING_VERIFICATION',
       'PENDING_ACTIVATION','ACTIVE','PREVENTIVELY_RESTRICTED','SUSPENDED','TERMINATED')
  NOT NULL;
