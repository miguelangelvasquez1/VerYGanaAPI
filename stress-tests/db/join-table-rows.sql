-- ============================================================================
-- join-table-rows.sql · huella del contenido de las 8 tablas de unión (spec 003)
--
-- Solo lectura. Por cada tabla: filas, pares distintos, filas con algún valor nulo y una huella
-- (suma de CRC32 de las columnas de datos). Nunca nombra la columna `id` ni usa `*`, así que da lo
-- mismo antes y después de la migración V202610041435: si la salida cambia, la migración tocó filas.
-- Sirve en local (run-sql.sh) y en DigitalOcean. No imprime datos personales: solo números.
--   stress-tests/scripts/run-sql.sh join-table-rows.sql <salida>
-- ============================================================================

SELECT 'answer_selected_options' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT answer_id, option_id) AS pares_distintos,
       IFNULL(SUM((answer_id IS NULL) OR (option_id IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(answer_id, 'NULL'), IFNULL(option_id, 'NULL')))), 0) AS huella
FROM answer_selected_options
UNION ALL
SELECT 'consumer_preferences' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT user_id, category_id) AS pares_distintos,
       IFNULL(SUM((user_id IS NULL) OR (category_id IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(user_id, 'NULL'), IFNULL(category_id, 'NULL')))), 0) AS huella
FROM consumer_preferences
UNION ALL
SELECT 'target_audience_categories' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT target_audience_id, category_id) AS pares_distintos,
       IFNULL(SUM((target_audience_id IS NULL) OR (category_id IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(target_audience_id, 'NULL'), IFNULL(category_id, 'NULL')))), 0) AS huella
FROM target_audience_categories
UNION ALL
SELECT 'target_audience_municipalities' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT target_audience_id, municipality_code) AS pares_distintos,
       IFNULL(SUM((target_audience_id IS NULL) OR (municipality_code IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(target_audience_id, 'NULL'), IFNULL(municipality_code, 'NULL')))), 0) AS huella
FROM target_audience_municipalities
UNION ALL
SELECT 'asset_definition_mime_types' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT asset_definition_id, mime_type) AS pares_distintos,
       IFNULL(SUM((asset_definition_id IS NULL) OR (mime_type IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(asset_definition_id, 'NULL'), IFNULL(mime_type, 'NULL')))), 0) AS huella
FROM asset_definition_mime_types
UNION ALL
SELECT 'commercial_onboarding_institutional_tools' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT commercial_onboarding_id, institutional_tool) AS pares_distintos,
       IFNULL(SUM((commercial_onboarding_id IS NULL) OR (institutional_tool IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(commercial_onboarding_id, 'NULL'), IFNULL(institutional_tool, 'NULL')))), 0) AS huella
FROM commercial_onboarding_institutional_tools
UNION ALL
SELECT 'commercial_onboarding_network_actors' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT commercial_onboarding_id, network_actor) AS pares_distintos,
       IFNULL(SUM((commercial_onboarding_id IS NULL) OR (network_actor IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(commercial_onboarding_id, 'NULL'), IFNULL(network_actor, 'NULL')))), 0) AS huella
FROM commercial_onboarding_network_actors
UNION ALL
SELECT 'commercial_onboarding_tech_needs' AS tabla,
       COUNT(*) AS filas,
       COUNT(DISTINCT commercial_onboarding_id, tech_need) AS pares_distintos,
       IFNULL(SUM((commercial_onboarding_id IS NULL) OR (tech_need IS NULL)), 0) AS filas_con_nulo,
       IFNULL(SUM(CRC32(CONCAT_WS('|', IFNULL(commercial_onboarding_id, 'NULL'), IFNULL(tech_need, 'NULL')))), 0) AS huella
FROM commercial_onboarding_tech_needs;
