-- Seed idempotente de campañas, audiencias y encuestas para beta/demo.
-- Los anuncios heredan sus categorías a través de target_audience_id y
-- target_audience_categories; las preferencias de consumidores usan
-- consumer_preferences.
-- Todos los creadores son los tres comerciales con public_id congelado en
-- beta_users.sql. No agregar marcas con nuevos usuarios por SQL: registrarlas
-- por POST /auth/register/commercial y completar /commercials/onboarding primero.

-- Asegura una preferencia para consumidores BETA usados en las respuestas y métricas.
INSERT INTO consumer_preferences (user_id, category_id)
SELECT cd.user_id, c.id
FROM (
    SELECT '0be7a000-0005-0000-0000-000000000004' AS public_id
    UNION ALL SELECT '0be7a000-0005-0000-0000-000000000005'
    UNION ALL SELECT '0be7a000-0005-0000-0000-000000000006'
    UNION ALL SELECT '0be7a000-0005-0000-0000-000000000008'
    UNION ALL SELECT '0be7a000-0005-0000-0000-000000000009'
) seeded_consumers
JOIN users u ON u.public_id = UUID_TO_BIN(seeded_consumers.public_id)
JOIN consumer_details cd ON cd.user_id = u.id
JOIN categories c ON c.name = 'Gastronomía'
WHERE NOT EXISTS (
    SELECT 1
    FROM consumer_preferences cp
    WHERE cp.user_id = cd.user_id
);

-- Audiencias diferenciadas por edad y género.
INSERT INTO target_audiences (min_age, max_age, target_gender)
SELECT seed.min_age, seed.max_age, seed.target_gender
FROM (
    SELECT 18 AS min_age, 45 AS max_age, 'ALL' AS target_gender
    UNION ALL SELECT 22, 40, 'ALL'
    UNION ALL SELECT 18, 34, 'FEMALE'
    UNION ALL SELECT 25, 50, 'ALL'
    UNION ALL SELECT 20, 45, 'ALL'
    UNION ALL SELECT 18, 40, 'ALL'
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM target_audiences ta
    WHERE ta.min_age = seed.min_age
      AND ta.max_age = seed.max_age
      AND ta.target_gender = seed.target_gender
);

-- Cada audiencia usada por anuncios y encuestas queda asociada a una categoría.
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT ta.id, c.id
FROM (
    -- Las audiencias 18-45/22-40 ALL eran de panadería (Trigo Dorado, BASIC);
    -- se retaguean a Tecnología porque sus 2 anuncios y su encuesta se
    -- movieron a Ecosistema Andino (ver más abajo) — BASIC no puede anunciar
    -- ni usar encuestas (canAdvertise=false, canUseSurveys=false,
    -- maxAds=0, maxSurveys=0 en PlanDataInitializer).
    SELECT 18 AS min_age, 45 AS max_age, 'ALL' AS target_gender, 'Tecnología' AS category_name
    UNION ALL SELECT 22, 40, 'ALL', 'Tecnología'
    UNION ALL SELECT 18, 34, 'FEMALE', 'Moda'
    UNION ALL SELECT 25, 50, 'ALL', 'Moda'
    UNION ALL SELECT 20, 45, 'ALL', 'Tecnología'
    UNION ALL SELECT 18, 40, 'ALL', 'Viajes'
) seed
JOIN target_audiences ta
  ON ta.min_age = seed.min_age
 AND ta.max_age = seed.max_age
 AND ta.target_gender = seed.target_gender
JOIN categories c ON c.name = seed.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM target_audience_categories tac
    WHERE tac.target_audience_id = ta.id
      AND tac.category_id = c.id
);

-- Reparación de un arranque previo: borra el tag "Gastronomía" que quedó de
-- cuando estas dos audiencias eran de Trigo Dorado. No-op si nunca se tuvo
-- esa versión.
DELETE tac FROM target_audience_categories tac
JOIN target_audiences ta ON ta.id = tac.target_audience_id
JOIN categories c ON c.id = tac.category_id
WHERE ta.min_age IN (18, 22) AND ta.max_age IN (45, 40) AND ta.target_gender = 'ALL'
  AND c.name = 'Gastronomía'
  AND ((ta.min_age = 18 AND ta.max_age = 45) OR (ta.min_age = 22 AND ta.max_age = 40));

-- Campañas activas para los tres comerciales congelados en beta_users.sql.
-- El usuario BASIC conserva el public_id real de beta_users.sql aunque su email
-- de demo no sea comercial.basic@verygana.com.
INSERT INTO ads (
    created_at,
    current_likes,
    description,
    end_date,
    max_likes,
    max_likes_per_user_per_day,
    reward_per_like,
    start_date,
    status,
    target_url,
    title,
    version,
    commercial_id,
    target_audience_id
)
SELECT
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    0,
    seed.description,
    DATE_ADD(NOW(), INTERVAL 60 DAY),
    seed.max_likes,
    1,
    seed.reward_per_like,
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    'ACTIVE',
    seed.target_url,
    seed.title,
    0,
    cd.user_id,
    ta.id
FROM (
    -- Estos dos anuncios eran de Trigo Dorado (BASIC). Se movieron a
    -- Ecosistema Andino (PREMIUM) porque BASIC no puede anunciar
    -- (canAdvertise=false, maxAds=0 en PlanDataInitializer) — no es solo un
    -- cambio de dueño, se reescribió el contenido para que calce con la
    -- marca (ver también la encuesta y target_audience_categories más abajo).
    SELECT
        '0be7a000-0004-0000-0000-000000000003' AS commercial_public_id,
        18 AS audience_min_age,
        45 AS audience_max_age,
        'ALL' AS audience_gender,
        'Energía solar para tu hogar' AS title,
        'Lámparas y paneles solares compactos para espacios pequeños, con garantía y soporte local de Ecosistema Andino.' AS description,
        20000 AS max_likes,
        2500 AS reward_per_like,
        'https://ecosistemaandino.co/energia-solar' AS target_url
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        22,
        40,
        'ALL',
        'Conectividad rural sin interrupciones',
        'Soluciones de internet satelital y conectividad pensadas para hogares y pequeños negocios en zonas rurales.',
        12000,
        1500,
        'https://ecosistemaandino.co/conectividad-rural'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000002',
        18,
        34,
        'FEMALE',
        'Estilo urbano para todos los días',
        'Explora prendas versátiles de producción colombiana, pensadas para combinar comodidad, diseño y uso cotidiano.',
        15000,
        3000,
        'https://modaurbana.co/nueva-coleccion'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000002',
        25,
        50,
        'ALL',
        'Renueva tus básicos con Moda Urbana',
        'Encuentra camisetas, chaquetas y accesorios esenciales con materiales durables y opciones para distintos estilos.',
        10000,
        1800,
        'https://modaurbana.co/basicos'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        20,
        45,
        'ALL',
        'Tecnología útil para una vida más simple',
        'Descubre soluciones tecnológicas seleccionadas para el hogar y el trabajo, con asesoría y soporte local.',
        12000,
        4000,
        'https://ecosistemaandino.co/soluciones'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        18,
        40,
        'ALL',
        'Planea tu próxima escapada natural',
        'Inspírate con experiencias de turismo responsable y recorridos para conocer paisajes de distintas regiones de Colombia.',
        14000,
        2200,
        'https://ecosistemaandino.co/escapadas'
) seed
JOIN users commercial_user
  ON commercial_user.public_id = UUID_TO_BIN(seed.commercial_public_id)
JOIN commercial_details cd ON cd.user_id = commercial_user.id
JOIN target_audiences ta
  ON ta.min_age = seed.audience_min_age
 AND ta.max_age = seed.audience_max_age
 AND ta.target_gender = seed.audience_gender
WHERE NOT EXISTS (
    SELECT 1
    FROM ads existing_ad
    WHERE existing_ad.commercial_id = cd.user_id
      AND existing_ad.title = seed.title
);

-- Asset de cada anuncio. AdLikeServiceImpl asume que todo anuncio ACTIVE tiene un
-- AdAsset (lee ad.getAsset().getDurationSeconds() sin null-check) tanto al
-- seleccionar el siguiente anuncio como al reanudar una visualización; sin esta
-- fila GET /ads/next lanza NullPointerException para cualquier consumidor.
--
-- Los videos reales (.mp4, 576x1024 h264/aac) viven en el bucket de beta
-- (public/ads/beta/<slug>.mp4) y se subieron a mano: BetaPlaceholderAssetSeeder
-- NO los genera. Si se resiembra contra un bucket sin esos archivos, el anuncio
-- queda sin medio. Tamaño y duración son los reales de cada archivo.
INSERT INTO ad_assets (
    object_key,
    size_bytes,
    media_type,
    mime_type,
    status,
    duration_seconds,
    ad_id,
    uploaded_at,
    version
)
SELECT
    CONCAT('ads/beta/', seed.slug, '.mp4'),
    seed.size_bytes,
    'VIDEO',
    'VIDEO_MP4',
    'ATTACHED',
    seed.duration_seconds,
    a.id,
    NOW(),
    0
FROM (
    SELECT 'Energía solar para tu hogar' AS ad_title, 'energia-solar-para-tu-hogar' AS slug, 27323382 AS size_bytes, 168 AS duration_seconds
    UNION ALL SELECT 'Conectividad rural sin interrupciones', 'conectividad-rural-sin-interrupciones', 6019399, 30
    UNION ALL SELECT 'Estilo urbano para todos los días', 'estilo-urbano-para-todos-los-dias', 1548438, 14
    UNION ALL SELECT 'Renueva tus básicos con Moda Urbana', 'renueva-tus-basicos-con-moda-urbana', 3070847, 16
    UNION ALL SELECT 'Tecnología útil para una vida más simple', 'tecnologia-util-para-una-vida-mas-simple', 2352529, 28
    UNION ALL SELECT 'Planea tu próxima escapada natural', 'planea-tu-proxima-escapada-natural', 31522837, 87
) seed
JOIN ads a ON a.title = seed.ad_title
WHERE NOT EXISTS (
    SELECT 1
    FROM ad_assets existing_asset
    WHERE existing_asset.ad_id = a.id
);

-- Migración de un arranque previo: versiones anteriores de este seed insertaron
-- el asset como IMAGE/.jpg de relleno. El INSERT de arriba es "solo si falta" y
-- no pisa filas ya creadas, así que este UPDATE las pasa al video real.
-- Idempotente: no-op si el asset ya está en .mp4/VIDEO.
UPDATE ad_assets existing_asset
JOIN ads a ON a.id = existing_asset.ad_id
JOIN (
    SELECT 'Energía solar para tu hogar' AS ad_title, 'energia-solar-para-tu-hogar' AS slug, 27323382 AS size_bytes, 168 AS duration_seconds
    UNION ALL SELECT 'Conectividad rural sin interrupciones', 'conectividad-rural-sin-interrupciones', 6019399, 30
    UNION ALL SELECT 'Estilo urbano para todos los días', 'estilo-urbano-para-todos-los-dias', 1548438, 14
    UNION ALL SELECT 'Renueva tus básicos con Moda Urbana', 'renueva-tus-basicos-con-moda-urbana', 3070847, 16
    UNION ALL SELECT 'Tecnología útil para una vida más simple', 'tecnologia-util-para-una-vida-mas-simple', 2352529, 28
    UNION ALL SELECT 'Planea tu próxima escapada natural', 'planea-tu-proxima-escapada-natural', 31522837, 87
) seed ON seed.ad_title = a.title
SET existing_asset.object_key = CONCAT('ads/beta/', seed.slug, '.mp4'),
    existing_asset.media_type = 'VIDEO',
    existing_asset.mime_type = 'VIDEO_MP4',
    existing_asset.size_bytes = seed.size_bytes,
    existing_asset.duration_seconds = seed.duration_seconds
WHERE existing_asset.media_type = 'IMAGE'
  AND existing_asset.object_key = CONCAT('ads/beta/', seed.slug, '.jpg');

-- Visualizaciones de ejemplo sin duplicar la combinación anuncio-consumidor.
INSERT INTO ad_watch_session (
    id,
    expires_at,
    resume_count,
    started_at,
    status,
    version,
    ad_id,
    consumer_user_id
)
SELECT
    UUID_TO_BIN(UUID()),
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    0,
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    'WATCHED',
    0,
    a.id,
    consumer_details.user_id
FROM (
    SELECT 'Energía solar para tu hogar' AS ad_title, '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id
    UNION ALL SELECT 'Energía solar para tu hogar', '0be7a000-0005-0000-0000-000000000005'
    UNION ALL SELECT 'Conectividad rural sin interrupciones', '0be7a000-0005-0000-0000-000000000006'
    UNION ALL SELECT 'Estilo urbano para todos los días', '0be7a000-0005-0000-0000-000000000008'
    UNION ALL SELECT 'Tecnología útil para una vida más simple', '0be7a000-0005-0000-0000-000000000009'
) seed
JOIN ads a ON a.title = seed.ad_title
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN consumer_details
  ON consumer_details.user_id = consumer_user.id
WHERE NOT EXISTS (
    SELECT 1
    FROM ad_watch_session aws
    WHERE aws.ad_id = a.id
      AND aws.consumer_user_id = consumer_details.user_id
);

-- Interacciones de ejemplo; current_likes se sincroniza con estas filas.
INSERT INTO ad_likes (created_at, reward_amount, consumer_user_id, ad_id)
SELECT
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    a.reward_per_like,
    consumer_details.user_id,
    a.id
FROM (
    SELECT
        '0be7a000-0004-0000-0000-000000000003' AS commercial_public_id,
        'Energía solar para tu hogar' AS ad_title,
        '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        'Energía solar para tu hogar',
        '0be7a000-0005-0000-0000-000000000005'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        'Conectividad rural sin interrupciones',
        '0be7a000-0005-0000-0000-000000000006'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000002',
        'Estilo urbano para todos los días',
        '0be7a000-0005-0000-0000-000000000006'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000002',
        'Estilo urbano para todos los días',
        '0be7a000-0005-0000-0000-000000000008'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        'Tecnología útil para una vida más simple',
        '0be7a000-0005-0000-0000-000000000006'
) seed
JOIN users commercial_user
  ON commercial_user.public_id = UUID_TO_BIN(seed.commercial_public_id)
JOIN ads a
  ON a.commercial_id = commercial_user.id
 AND a.title = seed.ad_title
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN consumer_details
  ON consumer_details.user_id = consumer_user.id
WHERE NOT EXISTS (
    SELECT 1
    FROM ad_likes existing_like
    WHERE existing_like.ad_id = a.id
      AND existing_like.consumer_user_id = consumer_details.user_id
);

UPDATE ads a
SET a.current_likes = (
    SELECT COUNT(*)
    FROM ad_likes al
    WHERE al.ad_id = a.id
)
WHERE a.title IN (
    'Energía solar para tu hogar',
    'Conectividad rural sin interrupciones',
    'Estilo urbano para todos los días',
    'Renueva tus básicos con Moda Urbana',
    'Tecnología útil para una vida más simple',
    'Planea tu próxima escapada natural'
)
AND a.commercial_id IN (
    SELECT cd.user_id
    FROM users u
    JOIN commercial_details cd ON cd.user_id = u.id
    WHERE u.public_id IN (
        UUID_TO_BIN('0be7a000-0004-0000-0000-000000000001'),
        UUID_TO_BIN('0be7a000-0004-0000-0000-000000000002'),
        UUID_TO_BIN('0be7a000-0004-0000-0000-000000000003')
    )
);

-- Encuestas activas; las dos primeras tendrán respuestas demo y la tercera no.
INSERT INTO surveys (
    created_at,
    description,
    ends_at,
    max_responses,
    response_count,
    reward_amount_per_question_cents,
    starts_at,
    status,
    title,
    creator_id,
    target_audience_id
)
SELECT
    NOW(),
    seed.description,
    DATE_ADD(NOW(), INTERVAL 90 DAY),
    seed.max_responses,
    0,
    seed.reward_amount_per_question_cents,
    DATE_SUB(NOW(), INTERVAL 2 DAY),
    'ACTIVE',
    seed.title,
    cd.user_id,
    ta.id
FROM (
    -- Era de Trigo Dorado (BASIC); se movió a Ecosistema Andino (PREMIUM) por
    -- la misma razón que los 2 anuncios de arriba: BASIC no puede usar
    -- encuestas (canUseSurveys=false, maxSurveys=0).
    SELECT
        '0be7a000-0004-0000-0000-000000000003' AS commercial_public_id,
        'Hábitos de consumo energético en el hogar' AS title,
        'Queremos entender cómo usas la energía y la conectividad en tu hogar para diseñar soluciones más eficientes.' AS description,
        5000 AS reward_amount_per_question_cents,
        250 AS max_responses,
        18 AS min_age,
        45 AS max_age,
        'ALL' AS target_gender
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000002',
        'Lo que buscas en la moda cotidiana',
        'Comparte qué prendas, estilos y atributos valoras al elegir ropa para tus actividades diarias.',
        6500,
        300,
        18,
        34,
        'FEMALE'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        'Interés en soluciones tecnológicas para el hogar',
        'Esta encuesta explora las necesidades de tecnología y conectividad en los hogares colombianos.',
        8000,
        200,
        20,
        45,
        'ALL'
) seed
JOIN users commercial_user
  ON commercial_user.public_id = UUID_TO_BIN(seed.commercial_public_id)
JOIN commercial_details cd ON cd.user_id = commercial_user.id
JOIN target_audiences ta
  ON ta.min_age = seed.min_age
 AND ta.max_age = seed.max_age
 AND ta.target_gender = seed.target_gender
WHERE NOT EXISTS (
    SELECT 1
    FROM surveys existing_survey
    WHERE existing_survey.creator_id = cd.user_id
      AND existing_survey.title = seed.title
);

-- Preguntas de selección única con opciones asociadas a cada encuesta.
INSERT INTO survey_questions (order_index, is_required, text, type, survey_id)
SELECT seed.order_index, 1, seed.question_text, 'SINGLE_CHOICE', s.id
FROM (
    SELECT 'Hábitos de consumo energético en el hogar' AS survey_title, 0 AS order_index,
           '¿Con qué frecuencia revisas tu consumo de energía o internet?' AS question_text
    UNION ALL SELECT 'Hábitos de consumo energético en el hogar', 1,
           '¿Qué influye más al elegir una solución de energía o conectividad?'
    UNION ALL SELECT 'Lo que buscas en la moda cotidiana', 0,
           '¿Qué tipo de prenda compras con mayor frecuencia?'
    UNION ALL SELECT 'Lo que buscas en la moda cotidiana', 1,
           '¿Qué valoras más en la ropa de uso diario?'
    UNION ALL SELECT 'Interés en soluciones tecnológicas para el hogar', 0,
           '¿Qué solución tecnológica te interesa mejorar en tu hogar?'
    UNION ALL SELECT 'Interés en soluciones tecnológicas para el hogar', 1,
           '¿Cuál es tu principal criterio al comprar tecnología?'
) seed
JOIN surveys s ON s.title = seed.survey_title
WHERE NOT EXISTS (
    SELECT 1
    FROM survey_questions sq
    WHERE sq.survey_id = s.id
      AND sq.text = seed.question_text
);

INSERT INTO question_options (order_index, text, question_id)
SELECT seed.order_index, seed.option_text, sq.id
FROM (
    SELECT '¿Con qué frecuencia revisas tu consumo de energía o internet?' AS question_text, 0 AS order_index, 'Varias veces por semana' AS option_text
    UNION ALL SELECT '¿Con qué frecuencia revisas tu consumo de energía o internet?', 1, 'Una vez por semana'
    UNION ALL SELECT '¿Con qué frecuencia revisas tu consumo de energía o internet?', 2, 'Una o dos veces al mes'
    UNION ALL SELECT '¿Qué influye más al elegir una solución de energía o conectividad?', 0, 'Ahorro y eficiencia'
    UNION ALL SELECT '¿Qué influye más al elegir una solución de energía o conectividad?', 1, 'Precio y financiación'
    UNION ALL SELECT '¿Qué influye más al elegir una solución de energía o conectividad?', 2, 'Facilidad de instalación'
    UNION ALL SELECT '¿Qué tipo de prenda compras con mayor frecuencia?', 0, 'Camisetas y prendas básicas'
    UNION ALL SELECT '¿Qué tipo de prenda compras con mayor frecuencia?', 1, 'Jeans y pantalones'
    UNION ALL SELECT '¿Qué tipo de prenda compras con mayor frecuencia?', 2, 'Chaquetas y prendas exteriores'
    UNION ALL SELECT '¿Qué valoras más en la ropa de uso diario?', 0, 'Comodidad y buen ajuste'
    UNION ALL SELECT '¿Qué valoras más en la ropa de uso diario?', 1, 'Diseño y variedad de estilos'
    UNION ALL SELECT '¿Qué valoras más en la ropa de uso diario?', 2, 'Durabilidad de los materiales'
    UNION ALL SELECT '¿Qué solución tecnológica te interesa mejorar en tu hogar?', 0, 'Conectividad a internet'
    UNION ALL SELECT '¿Qué solución tecnológica te interesa mejorar en tu hogar?', 1, 'Seguridad y monitoreo'
    UNION ALL SELECT '¿Qué solución tecnológica te interesa mejorar en tu hogar?', 2, 'Eficiencia energética'
    UNION ALL SELECT '¿Cuál es tu principal criterio al comprar tecnología?', 0, 'Facilidad de uso'
    UNION ALL SELECT '¿Cuál es tu principal criterio al comprar tecnología?', 1, 'Precio y garantía'
    UNION ALL SELECT '¿Cuál es tu principal criterio al comprar tecnología?', 2, 'Soporte técnico local'
) seed
JOIN survey_questions sq ON sq.text = seed.question_text
JOIN surveys s ON s.id = sq.survey_id
WHERE NOT EXISTS (
    SELECT 1
    FROM question_options qo
    WHERE qo.question_id = sq.id
      AND qo.text = seed.option_text
);

-- Respuestas completadas por consumidores BETA activos congelados en beta_users.sql.
INSERT INTO survey_sessions (
    completed_at,
    expires_at,
    started_at,
    status,
    version,
    consumer_id,
    survey_id
)
SELECT
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    DATE_ADD(NOW(), INTERVAL 1 DAY),
    DATE_SUB(NOW(), INTERVAL 1 DAY),
    'COMPLETED',
    0,
    cd.user_id,
    s.id
FROM (
    SELECT 'Hábitos de consumo energético en el hogar' AS survey_title, '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id
    UNION ALL SELECT 'Hábitos de consumo energético en el hogar', '0be7a000-0005-0000-0000-000000000005'
    UNION ALL SELECT 'Lo que buscas en la moda cotidiana', '0be7a000-0005-0000-0000-000000000008'
) seed
JOIN surveys s ON s.title = seed.survey_title
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN consumer_details cd ON cd.user_id = consumer_user.id
WHERE NOT EXISTS (
    SELECT 1
    FROM survey_sessions existing_session
    WHERE existing_session.survey_id = s.id
      AND existing_session.consumer_id = cd.user_id
);

INSERT INTO survey_answers (text_answer, question_id, selected_option_id, session_id)
SELECT NULL, sq.id, qo.id, ss.id
FROM (
    SELECT
        'Hábitos de consumo energético en el hogar' AS survey_title,
        '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id,
        '¿Con qué frecuencia revisas tu consumo de energía o internet?' AS question_text,
        'Una vez por semana' AS option_text
    UNION ALL SELECT
        'Hábitos de consumo energético en el hogar',
        '0be7a000-0005-0000-0000-000000000004',
        '¿Qué influye más al elegir una solución de energía o conectividad?',
        'Ahorro y eficiencia'
    UNION ALL SELECT
        'Hábitos de consumo energético en el hogar',
        '0be7a000-0005-0000-0000-000000000005',
        '¿Con qué frecuencia revisas tu consumo de energía o internet?',
        'Varias veces por semana'
    UNION ALL SELECT
        'Hábitos de consumo energético en el hogar',
        '0be7a000-0005-0000-0000-000000000005',
        '¿Qué influye más al elegir una solución de energía o conectividad?',
        'Facilidad de instalación'
    UNION ALL SELECT
        'Lo que buscas en la moda cotidiana',
        '0be7a000-0005-0000-0000-000000000008',
        '¿Qué tipo de prenda compras con mayor frecuencia?',
        'Camisetas y prendas básicas'
    UNION ALL SELECT
        'Lo que buscas en la moda cotidiana',
        '0be7a000-0005-0000-0000-000000000008',
        '¿Qué valoras más en la ropa de uso diario?',
        'Comodidad y buen ajuste'
) seed
JOIN surveys s ON s.title = seed.survey_title
JOIN survey_questions sq
  ON sq.survey_id = s.id
 AND sq.text = seed.question_text
JOIN question_options qo
  ON qo.question_id = sq.id
 AND qo.text = seed.option_text
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN survey_sessions ss
  ON ss.survey_id = s.id
 AND ss.consumer_id = consumer_user.id
 AND ss.status = 'COMPLETED'
WHERE NOT EXISTS (
    SELECT 1
    FROM survey_answers existing_answer
    WHERE existing_answer.session_id = ss.id
      AND existing_answer.question_id = sq.id
);

UPDATE surveys s
SET s.response_count = (
    SELECT COUNT(*)
    FROM survey_sessions ss
    WHERE ss.survey_id = s.id
      AND ss.status = 'COMPLETED'
)
WHERE s.title IN (
    'Encuesta sobre compras en panaderías locales',
    'Lo que buscas en la moda cotidiana',
    'Interés en soluciones tecnológicas para el hogar'
);

-- ============================================================
-- ONBOARDING COMERCIAL (parche demo) — comerciales congelados
-- ============================================================
-- beta_users.sql crea users + commercial_details de los 3 comerciales
-- (BASIC/STANDARD/PREMIUM) pero deliberadamente NO crea su fila en
-- commercial_onboarding (ese alcance es de finanzas/onboarding). Sin esa
-- fila, GET /commercials/onboarding/status lanza ObjectNotFoundException
-- y el front los bloquea apenas inician sesión (no pueden ni ver su
-- dashboard para crear campañas de brandeo).
--
-- Este INSERT los marca como current_step = COMPLETED directamente, sin
-- pasar por el wizard real (términos, identificación jurídica,
-- diagnóstico, clasificación de ruta, plan, documentos). Es un atajo solo
-- para demo/beta: NO crea commercial_contracts, así que si el front llega
-- a pedir ver/descargar "su contrato firmado" para alguno de estos 3, no
-- va a encontrar nada.
--
-- person_type sí se fija explícitamente (JURIDICA: las 3 empresas son S.A.S.)
-- porque al saltarse el paso de identificación jurídica quedaba NULL, y el
-- front interpreta ese NULL como "persona natural" / "plan personal" — con
-- eso bloqueaba el dashboard del comercial aunque current_step ya estuviera
-- en COMPLETED y el plan fuera el correcto. Ver CommercialDocumentServiceImpl
-- (CAMARA_COMERCIO solo aplica a JURIDICA).
--
-- Idempotente vía la UNIQUE KEY real de la tabla (commercial_details_id): el
-- ON DUPLICATE actualiza person_type para que una fila vieja con NULL (de
-- antes de este fix) también quede corregida en el próximo arranque.
INSERT INTO commercial_onboarding (
    commercial_details_id,
    current_step,
    created_at,
    completed_at,
    terms_accepted_at,
    legal_identification_completed_at,
    diagnostic_completed_at,
    person_type,
    route,
    route_preliminary,
    verification_required,
    classified_at,
    route_confirmed,
    route_confirmed_at,
    selected_plan_id,
    plan_accepted_at,
    documents_completed_at
)
SELECT
    cd.user_id,
    'COMPLETED',
    NOW(),
    NOW(),
    NOW(),
    NOW(),
    NOW(),
    'JURIDICA',
    'A',
    0,
    0,
    NOW(),
    1,
    NOW(),
    p.id,
    NOW(),
    NOW()
FROM (
    SELECT '0be7a000-0004-0000-0000-000000000001' AS commercial_public_id, 'BASIC' AS plan_code
    UNION ALL SELECT '0be7a000-0004-0000-0000-000000000002', 'STANDARD'
    UNION ALL SELECT '0be7a000-0004-0000-0000-000000000003', 'PREMIUM'
) seed
JOIN users u ON u.public_id = UUID_TO_BIN(seed.commercial_public_id)
JOIN commercial_details cd ON cd.user_id = u.id
JOIN plans p ON p.code = seed.plan_code AND p.active = 1
ON DUPLICATE KEY UPDATE person_type = VALUES(person_type);

-- El saldo de wallet de estos comerciales (y de cualquier otro que se
-- agregue) se maneja en beta_finance.sql con montos por plan — no
-- duplicar aquí (una wallet creada primero con un monto genérico hace
-- que el INSERT ... WHERE NOT EXISTS de beta_finance.sql no haga nada).
