-- Llave primaria en las 8 tablas de unión y de listas de valores que V1 dejó sin ella (spec 003).
--
-- DigitalOcean Managed MySQL exige llave primaria en toda tabla (sql_require_primary_key). Cada
-- tabla recibe una columna id bigint AUTO_INCREMENT, INVISIBLE (MySQL 8.0.23 o posterior), como
-- llave primaria: no aparece en SELECT * ni cuenta en un INSERT sin lista de columnas, y Hibernate
-- no la ve. Las columnas actuales no se tocan: la llave va solo sobre id, así que los pares
-- repetidos y los valores nulos que ya existan se conservan (no borra ni modifica ninguna fila).
--
-- Una sola sentencia por tabla (columna y llave juntas): una columna AUTO_INCREMENT tiene que ser
-- llave (error 1075) y con sql_require_primary_key = ON un ALTER que deje la tabla sin llave se
-- rechaza (error 3750). Así funciona con el requisito apagado o encendido.
--
-- Cada bloque es condicional: solo actúa si la tabla existe y no tiene llave primaria (MySQL no
-- tiene ADD COLUMN IF NOT EXISTS ni hace DDL transaccional). Si el arranque se corta a la mitad,
-- las tablas ya hechas se saltan al repetir; antes hay que borrar la fila fallida de
-- flyway_schema_history. No crea tablas, triggers ni funciones: compatible con un usuario sin SUPER.

-- ─── answer_selected_options ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'answer_selected_options'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE answer_selected_options ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── consumer_preferences ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'consumer_preferences'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE consumer_preferences ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── target_audience_categories ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'target_audience_categories'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE target_audience_categories ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── target_audience_municipalities ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'target_audience_municipalities'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE target_audience_municipalities ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── asset_definition_mime_types ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'asset_definition_mime_types'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE asset_definition_mime_types ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── commercial_onboarding_institutional_tools ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'commercial_onboarding_institutional_tools'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE commercial_onboarding_institutional_tools ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── commercial_onboarding_network_actors ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'commercial_onboarding_network_actors'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE commercial_onboarding_network_actors ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ─── commercial_onboarding_tech_needs ───
SET @needs_pk := (
    SELECT COUNT(*) FROM information_schema.tables t
    WHERE t.table_schema = DATABASE() AND t.table_name = 'commercial_onboarding_tech_needs'
      AND NOT EXISTS (
          SELECT 1 FROM information_schema.table_constraints c
          WHERE c.table_schema = t.table_schema AND c.table_name = t.table_name
            AND c.constraint_type = 'PRIMARY KEY')
);
SET @ddl := IF(@needs_pk = 1,
    'ALTER TABLE commercial_onboarding_tech_needs ADD COLUMN id bigint NOT NULL AUTO_INCREMENT INVISIBLE PRIMARY KEY',
    'SELECT 1');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
