-- Contenido de marca que carga el anunciante en la solicitud (BrandingRequest.briefData):
-- las preguntas de la trivia, las palabras de la sopa de letras, las pistas del
-- crucigrama. Va aparte de draft_form_data, que es del diseñador.
--
-- Condicional porque las bases de desarrollo que corrieron con ddl-auto: update ya
-- tienen la columna, y MySQL no soporta ADD COLUMN IF NOT EXISTS: sin el chequeo,
-- esta migración fallaría ahí y la app no arrancaría.
SET @brief_data_exists := (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'branding_requests'
      AND column_name = 'brief_data'
);

SET @ddl := IF(@brief_data_exists = 0,
    'ALTER TABLE branding_requests ADD COLUMN brief_data json DEFAULT NULL AFTER draft_form_data',
    'SELECT 1');

PREPARE add_brief_data FROM @ddl;
EXECUTE add_brief_data;
DEALLOCATE PREPARE add_brief_data;
