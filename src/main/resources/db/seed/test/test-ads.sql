-- ============================================================
-- SEED: Anuncios de prueba para validar llaves × nivel
-- Idempotente. Depende de test-users.sql (comercial).
--
-- reward_per_like = 10000 centavos = 10 llaves base (key-value-cents=1000).
-- Con el multiplicador de nivel:
--   BRONCE  (x0.5) → 5.000 centavos  = 5 llaves
--   ORO     (x0.7) → 7.000 centavos  = 7 llaves
--   DIAMANTE(x1.0) → 10.000 centavos = 10 llaves
--
-- duration_seconds = 5: el like exige haber "visto" >= 95% del video,
-- así que en la prueba espera ~5s entre /next y /like.
-- Sin targeting (fechas, municipios, edad, género NULL) → elegible
-- para cualquier consumer.
-- ============================================================

SET @commercial_id = (SELECT cd.user_id FROM commercial_details cd
                      JOIN users u ON u.id = cd.user_id
                      WHERE u.email = 'comercial@verygana.com' LIMIT 1);

-- ============================================================
-- AD 900 — video corto de prueba
-- ============================================================

-- Target audience del anuncio (sin edad/género); la categoría Tecnología se
-- enlaza más abajo. Id fijo = id del anuncio para mantener el seed idempotente.
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 900, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 900);

INSERT INTO ads (
    id, version, title, description,
    reward_per_like, max_likes, current_likes, max_likes_per_user_per_day,
    status, created_at, updated_at,
    start_date, end_date, commercial_id, target_url,
    target_audience_id
)
SELECT
    900, 1,
    'Anuncio Test - Llaves x Nivel',
    'Anuncio de prueba para validar el multiplicador de nivel sobre las llaves.',
    10000,      -- reward_per_like: 10 llaves base
    100, 0, NULL,
    'ACTIVE', NOW(), NOW(),
    NULL, NULL, @commercial_id, 'https://verygana.com',
    900
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE id = 900);

INSERT INTO ad_assets (
    id, object_key, size_bytes, media_type, mime_type,
    status, duration_seconds, ad_id, uploaded_at
)
SELECT
    900, 'ads/test/video-test-900.mp4', 1024, 'VIDEO', 'VIDEO_MP4',
    'ATTACHED', 5, 900, NOW()
WHERE NOT EXISTS (SELECT 1 FROM ad_assets WHERE id = 900);

-- ============================================================
-- AD 901 — segundo anuncio (para probar con varios usuarios
-- sin esperar el cooldown del primero)
-- ============================================================

INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 901, NULL, NULL, NULL
WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 901);

INSERT INTO ads (
    id, version, title, description,
    reward_per_like, max_likes, current_likes, max_likes_per_user_per_day,
    status, created_at, updated_at,
    start_date, end_date, commercial_id, target_url,
    target_audience_id
)
SELECT
    901, 1,
    'Anuncio Test 2 - Llaves x Nivel',
    'Segundo anuncio de prueba para comparar niveles.',
    10000,
    100, 0, NULL,
    'ACTIVE', NOW(), NOW(),
    NULL, NULL, @commercial_id, 'https://verygana.com',
    901
WHERE NOT EXISTS (SELECT 1 FROM ads WHERE id = 901);

INSERT INTO ad_assets (
    id, object_key, size_bytes, media_type, mime_type,
    status, duration_seconds, ad_id, uploaded_at
)
SELECT
    901, 'ads/test/video-test-901.mp4', 1024, 'VIDEO', 'VIDEO_MP4',
    'ATTACHED', 5, 901, NOW()
WHERE NOT EXISTS (SELECT 1 FROM ad_assets WHERE id = 901);

-- ============================================================
-- CATEGORÍAS de los anuncios (vía target_audience_categories)
-- La segmentación por categoría vive ahora en el TargetAudience
-- del anuncio (target_audiences 900/901 creados arriba).
-- ============================================================

INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 900, c.id FROM categories c
WHERE c.name = 'Tecnología'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac
                WHERE tac.target_audience_id = 900 AND tac.category_id = c.id);

INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 901, c.id FROM categories c
WHERE c.name = 'Tecnología'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac
                WHERE tac.target_audience_id = 901 AND tac.category_id = c.id);