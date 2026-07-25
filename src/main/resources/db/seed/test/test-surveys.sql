-- ============================================================
-- TEST SURVEYS — cubre los distintos estados y tipos de pregunta
--
-- Survey 1: ACTIVE — general, todos los tipos de pregunta
-- Survey 2: ACTIVE — con targeting de género y edad
-- Survey 3: ACTIVE — solo MULTIPLE_CHOICE
-- Survey 4: PAUSED  — para verificar que no aparece al consumer
-- Survey 5: DRAFT   — para verificar que no aparece al consumer
--
-- Re-ejecutable: borra y recrea por título
-- ============================================================

-- Limpiar datos previos. Los FK NO tienen ON DELETE CASCADE (Hibernate
-- no lo genera), así que se borra de hijos a padres.

-- 1. Opciones seleccionadas en respuestas (nieto de sessions)
DELETE aso FROM answer_selected_options aso
JOIN survey_answers sa ON sa.id = aso.answer_id
JOIN survey_sessions ss ON ss.id = sa.session_id
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia',
    'Hábitos de alimentación');

-- 2. Respuestas
DELETE sa FROM survey_answers sa
JOIN survey_sessions ss ON ss.id = sa.session_id
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia',
    'Hábitos de alimentación');

-- 3. Recompensas de sesiones
DELETE sr FROM survey_rewards sr
JOIN survey_sessions ss ON ss.id = sr.session_id
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia',
    'Hábitos de alimentación');

-- 4. Sesiones
DELETE ss FROM survey_sessions ss
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia',
    'Hábitos de alimentación');

-- 5. Opciones de preguntas
DELETE qo FROM question_options qo
JOIN survey_questions sq ON sq.id = qo.question_id
JOIN surveys s ON s.id = sq.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia',
    'Hábitos de alimentación');

-- 6. Preguntas
DELETE sq FROM survey_questions sq
JOIN surveys s ON s.id = sq.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia',
    'Hábitos de alimentación');

-- 7. Encuestas. Sus target_audiences usan ids fijos (950-954), son idempotentes
--    y persisten entre corridas: abajo se re-enlazan con NOT EXISTS, por eso no
--    hace falta borrarlos aquí (evita romper/recrear la FK).
DELETE FROM surveys WHERE title IN (
    'Hábitos digitales',
    'Tendencias de moda femenina',
    'Preferencias de entretenimiento',
    'Experiencia de compra',
    'Videojuegos en Colombia',
    'Hábitos de alimentación'
);

-- surveys.creator_id es NOT NULL. Usamos el comercial de prueba como creador
-- (sembrado en test-users.sql, que corre antes que este script).
SET @commercial_id = (
    SELECT cd.user_id FROM commercial_details cd
    JOIN users u ON u.id = cd.user_id
    WHERE u.email = 'comercial@verygana.com'
);


-- ============================================================
-- SURVEY 1 — "Hábitos digitales" (ACTIVE, sin targeting)
--   Cubre: SINGLE_CHOICE, YES_NO, RATING, TEXT
--   Recompensa: 500 centavos por pregunta
-- ============================================================

INSERT INTO surveys (
    title, description,
    reward_amount_per_question_cents,
    max_responses, response_count,
    status, starts_at, ends_at,
    creator_id
) VALUES (
    'Hábitos digitales',
    '¿Cómo usas la tecnología en tu día a día? Queremos conocerte mejor.',
    500, 200, 0,
    'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY),
    @commercial_id
);

-- Segmentación: categoría Tecnología, sin filtro de edad/género
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 950, NULL, NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 950);
UPDATE surveys SET target_audience_id = 950 WHERE title = 'Hábitos digitales';
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 950, c.id FROM categories c WHERE c.name = 'Tecnología'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 950 AND tac.category_id = c.id);

-- Q1: SINGLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Cuál es tu red social favorita?', 'SINGLE_CHOICE', 0, true
FROM surveys WHERE title = 'Hábitos digitales';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Instagram', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos digitales' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'TikTok', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos digitales' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'YouTube', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos digitales' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'X (Twitter)', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos digitales' AND q.order_index = 0;

-- Q2: YES_NO
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Usas tu celular más de 4 horas al día?', 'YES_NO', 1, true
FROM surveys WHERE title = 'Hábitos digitales';

-- Q3: RATING
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Qué tan satisfecho estás con tu servicio de internet? (1 = muy malo, 5 = excelente)', 'RATING', 2, true
FROM surveys WHERE title = 'Hábitos digitales';

-- Q4: TEXT
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Qué app te gustaría que existiera y aún no existe?', 'TEXT', 3, false
FROM surveys WHERE title = 'Hábitos digitales';


-- ============================================================
-- SURVEY 2 — "Tendencias de moda femenina" (ACTIVE, mujer 18-35)
--   Cubre: SINGLE_CHOICE, MULTIPLE_CHOICE, RATING
--   Recompensa: 800 centavos por pregunta
-- ============================================================

INSERT INTO surveys (
    title, description,
    reward_amount_per_question_cents,
    max_responses, response_count,
    status, starts_at, ends_at,
    creator_id
) VALUES (
    'Tendencias de moda femenina',
    'Ayúdanos a entender las preferencias de moda de las mujeres colombianas.',
    800, 150, 0,
    'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 21 DAY),
    @commercial_id
);

-- Segmentación: mujeres 18-35, categoría Moda
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 951, 18, 35, 'FEMALE' WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 951);
UPDATE surveys SET target_audience_id = 951 WHERE title = 'Tendencias de moda femenina';
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 951, c.id FROM categories c WHERE c.name = 'Moda'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 951 AND tac.category_id = c.id);

-- Q1: SINGLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Dónde compras ropa con más frecuencia?', 'SINGLE_CHOICE', 0, true
FROM surveys WHERE title = 'Tendencias de moda femenina';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Tiendas físicas del centro comercial', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Tiendas online (Shein, Zara, etc.)', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Mercado o tienda de barrio', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Redes sociales (Instagram, TikTok)', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 0;

-- Q2: MULTIPLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Qué estilos de ropa usas normalmente? (puedes elegir varios)', 'MULTIPLE_CHOICE', 1, true
FROM surveys WHERE title = 'Tendencias de moda femenina';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Casual / Cotidiano', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Deportivo / Athleisure', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Elegante / Formal', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Urbano / Streetwear', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Bohemio / Artesanal', 4 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Tendencias de moda femenina' AND q.order_index = 1;

-- Q3: RATING
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Cuánto valoras la marca al comprar ropa? (1 = no importa, 5 = muy importante)', 'RATING', 2, true
FROM surveys WHERE title = 'Tendencias de moda femenina';


-- ============================================================
-- SURVEY 3 — "Preferencias de entretenimiento" (ACTIVE)
--   Cubre: MULTIPLE_CHOICE, YES_NO
--   Recompensa: 300 centavos por pregunta
-- ============================================================

INSERT INTO surveys (
    title, description,
    reward_amount_per_question_cents,
    max_responses, response_count,
    status, starts_at, ends_at,
    creator_id
) VALUES (
    'Preferencias de entretenimiento',
    '¿Qué haces en tu tiempo libre? Tu opinión nos ayuda a mejorar la experiencia.',
    300, 500, 0,
    'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 45 DAY),
    @commercial_id
);

-- Segmentación: categorías Cine y Series + Música
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 952, NULL, NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 952);
UPDATE surveys SET target_audience_id = 952 WHERE title = 'Preferencias de entretenimiento';
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 952, c.id FROM categories c WHERE c.name = 'Cine y Series'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 952 AND tac.category_id = c.id);
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 952, c.id FROM categories c WHERE c.name = 'Música'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 952 AND tac.category_id = c.id);

-- Q1: MULTIPLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Cuáles plataformas de streaming usas? (puedes marcar varias)', 'MULTIPLE_CHOICE', 0, true
FROM surveys WHERE title = 'Preferencias de entretenimiento';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Netflix', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Disney+', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Max (HBO)', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Prime Video', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'YouTube', 4 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Ninguna', 5 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 0;

-- Q2: MULTIPLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Qué géneros musicales escuchas? (puedes elegir varios)', 'MULTIPLE_CHOICE', 1, true
FROM surveys WHERE title = 'Preferencias de entretenimiento';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Reggaeton / Urbano', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Pop', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Rock / Metal', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Salsa / Cumbia / Vallenato', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Electrónica / House', 4 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Preferencias de entretenimiento' AND q.order_index = 1;

-- Q3: YES_NO
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Asistes a conciertos o eventos en vivo al menos una vez al año?', 'YES_NO', 2, false
FROM surveys WHERE title = 'Preferencias de entretenimiento';


-- ============================================================
-- SURVEY 4 — "Experiencia de compra" (PAUSED)
-- ============================================================

INSERT INTO surveys (
    title, description,
    reward_amount_per_question_cents,
    max_responses, response_count,
    status, starts_at, ends_at,
    creator_id
) VALUES (
    'Experiencia de compra',
    'Cuéntanos sobre tu última compra en línea.',
    600, 100, 0,
    'PAUSED', NOW(), DATE_ADD(NOW(), INTERVAL 15 DAY),
    @commercial_id
);

-- Segmentación: categoría Tecnología
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 953, NULL, NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 953);
UPDATE surveys SET target_audience_id = 953 WHERE title = 'Experiencia de compra';
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 953, c.id FROM categories c WHERE c.name = 'Tecnología'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 953 AND tac.category_id = c.id);

INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Qué tan seguido compras en línea?', 'SINGLE_CHOICE', 0, true
FROM surveys WHERE title = 'Experiencia de compra';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Todos los días', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Experiencia de compra' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Varias veces a la semana', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Experiencia de compra' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Una vez al mes', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Experiencia de compra' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Casi nunca', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Experiencia de compra' AND q.order_index = 0;


-- ============================================================
-- SURVEY 5 — "Videojuegos en Colombia" (DRAFT)
-- ============================================================

INSERT INTO surveys (
    title, description,
    reward_amount_per_question_cents,
    max_responses, response_count,
    status, starts_at, ends_at,
    creator_id
) VALUES (
    'Videojuegos en Colombia',
    'Encuesta sobre hábitos de gaming en Colombia. Próximamente activa.',
    1000, 300, 0,
    'DRAFT', DATE_ADD(NOW(), INTERVAL 7 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY),
    @commercial_id
);

-- Segmentación: categoría Videojuegos
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 954, NULL, NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 954);
UPDATE surveys SET target_audience_id = 954 WHERE title = 'Videojuegos en Colombia';
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 954, c.id FROM categories c WHERE c.name = 'Videojuegos'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 954 AND tac.category_id = c.id);

INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Con qué frecuencia juegas videojuegos?', 'SINGLE_CHOICE', 0, true
FROM surveys WHERE title = 'Videojuegos en Colombia';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Todos los días', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Videojuegos en Colombia' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Fines de semana', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Videojuegos en Colombia' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Ocasionalmente', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Videojuegos en Colombia' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'No juego', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Videojuegos en Colombia' AND q.order_index = 0;


-- ============================================================
-- SURVEY 6 — "Hábitos de alimentación" (ACTIVE)
--   Cubre: SINGLE_CHOICE, MULTIPLE_CHOICE, YES_NO, RATING, TEXT
--   Recompensa: 700 centavos por pregunta
-- ============================================================

INSERT INTO surveys (
    title, description,
    reward_amount_per_question_cents,
    max_responses, response_count,
    status, starts_at, ends_at,
    creator_id
) VALUES (
    'Hábitos de alimentación',
    'Queremos conocer cómo comes en tu día a día para ofrecerte mejores recompensas.',
    700, 400, 0,
    'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY),
    @commercial_id
);

-- Segmentación: categoría Gastronomía, sin filtro de edad/género
INSERT INTO target_audiences (id, min_age, max_age, target_gender)
SELECT 955, NULL, NULL, NULL WHERE NOT EXISTS (SELECT 1 FROM target_audiences WHERE id = 955);
UPDATE surveys SET target_audience_id = 955 WHERE title = 'Hábitos de alimentación';
INSERT INTO target_audience_categories (target_audience_id, category_id)
SELECT 955, c.id FROM categories c WHERE c.name = 'Gastronomía'
AND NOT EXISTS (SELECT 1 FROM target_audience_categories tac WHERE tac.target_audience_id = 955 AND tac.category_id = c.id);

-- Q1: SINGLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Cuántas comidas haces al día normalmente?', 'SINGLE_CHOICE', 0, true
FROM surveys WHERE title = 'Hábitos de alimentación';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, '1 o 2', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, '3 comidas', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, '3 comidas + snacks', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 0;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Como a cualquier hora', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 0;

-- Q2: MULTIPLE_CHOICE
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Dónde sueles conseguir tu comida? (puedes elegir varios)', 'MULTIPLE_CHOICE', 1, true
FROM surveys WHERE title = 'Hábitos de alimentación';

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Cocino en casa', 0 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Domicilios (Rappi, iFood, etc.)', 1 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Restaurantes', 2 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Comida rápida', 3 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 1;

INSERT INTO question_options (question_id, text, order_index)
SELECT q.id, 'Tiendas de barrio / mercado', 4 FROM survey_questions q JOIN surveys s ON s.id = q.survey_id
WHERE s.title = 'Hábitos de alimentación' AND q.order_index = 1;

-- Q3: YES_NO
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Lees la información nutricional antes de comprar un producto?', 'YES_NO', 2, true
FROM surveys WHERE title = 'Hábitos de alimentación';

-- Q4: RATING
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Qué tan saludable consideras tu alimentación? (1 = nada, 5 = muy saludable)', 'RATING', 3, true
FROM surveys WHERE title = 'Hábitos de alimentación';

-- Q5: TEXT
INSERT INTO survey_questions (survey_id, text, type, order_index, is_required)
SELECT id, '¿Cuál es tu plato favorito?', 'TEXT', 4, false
FROM surveys WHERE title = 'Hábitos de alimentación';
