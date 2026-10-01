-- Agrega disponibilidad de WhatsApp y número de contacto al perfil comercial.
-- Ver CommercialDetails#whatsappAvailable / #whatsappNumber.
--
-- Entró como V2, pero V2 ya era add_birth_date_to_consumer_details y Flyway se niega
-- a arrancar con dos migraciones de la misma versión. Va como V11, después de V9
-- (Pablo) y V10 (Nicolás).
--
-- Condicional porque las bases de desarrollo que corrieron con ddl-auto: update ya
-- pueden tener las columnas, y MySQL no soporta ADD COLUMN IF NOT EXISTS.

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'commercial_details' AND column_name = 'whatsapp_available'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE commercial_details ADD COLUMN whatsapp_available TINYINT(1) NOT NULL DEFAULT 0',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'commercial_details' AND column_name = 'whatsapp_number'
);
SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE commercial_details ADD COLUMN whatsapp_number VARCHAR(20) NULL',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
