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
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 2. Respuestas
DELETE sa FROM survey_answers sa
JOIN survey_sessions ss ON ss.id = sa.session_id
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 3. Recompensas de sesiones
DELETE sr FROM survey_rewards sr
JOIN survey_sessions ss ON ss.id = sr.session_id
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 4. Sesiones
DELETE ss FROM survey_sessions ss
JOIN surveys s ON s.id = ss.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 5. Opciones de preguntas
DELETE qo FROM question_options qo
JOIN survey_questions sq ON sq.id = qo.question_id
JOIN surveys s ON s.id = sq.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 6. Preguntas
DELETE sq FROM survey_questions sq
JOIN surveys s ON s.id = sq.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 7. Categorías (tabla de unión)
DELETE sc FROM survey_categories sc
JOIN surveys s ON s.id = sc.survey_id
WHERE s.title IN ('Hábitos digitales', 'Tendencias de moda femenina',
    'Preferencias de entretenimiento', 'Experiencia de compra', 'Videojuegos en Colombia');

-- 8. Encuestas
DELETE FROM surveys WHERE title IN (
    'Hábitos digitales',
    'Tendencias de moda femenina',
    'Preferencias de entretenimiento',
    'Experiencia de compra',
    'Videojuegos en Colombia'
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
    NULL
);

INSERT INTO survey_categories (survey_id, category_id)
SELECT s.id, c.id FROM surveys s JOIN categories c ON c.name = 'Tecnología'
WHERE s.title = 'Hábitos digitales';

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
    min_age, max_age, target_gender,
    creator_id
) VALUES (
    'Tendencias de moda femenina',
    'Ayúdanos a entender las preferencias de moda de las mujeres colombianas.',
    800, 150, 0,
    'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 21 DAY),
    18, 35, 'FEMALE',
    NULL
);

INSERT INTO survey_categories (survey_id, category_id)
SELECT s.id, c.id FROM surveys s JOIN categories c ON c.name = 'Moda'
WHERE s.title = 'Tendencias de moda femenina';

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
    NULL
);

INSERT INTO survey_categories (survey_id, category_id)
SELECT s.id, c.id FROM surveys s JOIN categories c ON c.name = 'Cine y Series'
WHERE s.title = 'Preferencias de entretenimiento';

INSERT INTO survey_categories (survey_id, category_id)
SELECT s.id, c.id FROM surveys s JOIN categories c ON c.name = 'Música'
WHERE s.title = 'Preferencias de entretenimiento';

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
    NULL
);

INSERT INTO survey_categories (survey_id, category_id)
SELECT s.id, c.id FROM surveys s JOIN categories c ON c.name = 'Tecnología'
WHERE s.title = 'Experiencia de compra';

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
    NULL
);

INSERT INTO survey_categories (survey_id, category_id)
SELECT s.id, c.id FROM surveys s JOIN categories c ON c.name = 'Videojuegos'
WHERE s.title = 'Videojuegos en Colombia';

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
