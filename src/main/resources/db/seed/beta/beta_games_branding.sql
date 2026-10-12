-- Seed idempotente del brandeo de los 20 juegos sembrados en db/seed/games/
-- (SCRUM-83). Una sola marca, pareja y recompensa financiera por juego: todo
-- vive bajo el comercial de demo comercial.premium@verygana.com (Ecosistema
-- Andino S.A.S, ya creado en beta_users.sql) para no depender de cuentas de
-- empresario nuevas — SCRUM-81 no es un prerrequisito de este archivo.
--
-- Alcance real: 18 de los 20 juegos. Pendientes, sin seedear aquí:
--   - Avoid The Bomb / Frisby (game_id=1): la solicitud quedó en
--     CHANGES_REQUESTED, nunca se aprobó.
--   - Balloon Lift (game_id=3): el único intento (Éxito) fue RECHAZADO; Éxito
--     se reasignó a Catch It (game_id=4) y Balloon Lift se quedó sin marca.
-- Agregar esos dos en un seed aparte cuando tengan una solicitud aprobada.
--
-- Pendiente de compliance (no resuelto en este seed, a propósito): Ball
-- Bounce/Aguardiente Amarillo (game_id=2) y Whack A Mole/Póker (game_id=10)
-- son contenido de alcohol. Ninguno de los dos trae la advertencia legal
-- "El exceso de alcohol es perjudicial para la salud" en sus textos, y
-- ninguno tiene target_audience_id con mínimo de edad 18. Revisar y corregir
-- desde la app antes de usar esta demo con audiencia externa.
--
-- Dedupe por (game_id, commercial_id, brand_name) en vez de (game_id,
-- commercial_id): ese comercial ya tiene una branding_request de
-- beta_finance.sql sobre el juego 19 (Trivia Quiz, marca "Ecosistema
-- Andino"); Parque del Café es una marca distinta sobre el mismo juego y
-- mismo comercial, así que colisionaría con la clave más corta. Las
-- campañas (que no tienen brand_name) se dedupean por meta.brand_id dentro
-- de config_data, ya que todo game_config de este archivo lo trae.
--
-- game_config_definition_id se resuelve siempre por (game_id, is_latest=1):
-- nunca por id literal, para no depender de qué definiciones existan ya en
-- cada entorno.
--
-- Los game_config de este archivo son los reales, resueltos y probados en
-- el flujo de brandeo (incluyen URLs reales de cdn.verygana.com, ya
-- públicas). No son placeholders.
-- Resueltas y con campaña activa (18 de los 20 juegos). Pendientes: Avoid The Bomb
-- (Frisby, quedó en CHANGES_REQUESTED) y Balloon Lift (sin marca asignada: el único
-- intento con Éxito fue RECHAZADO). Se agregan en un seed aparte cuando se resuelvan.
--
-- Dedupe por (game_id, commercial_id, brand_name) y no solo (game_id, commercial_id):
-- este comercial (Ecosistema Andino S.A.S) ya tiene una branding_request de
-- beta_finance.sql sobre el juego 19 (Trivia Quiz); Parque del Café es una marca
-- distinta sobre el mismo juego y commercial, así que colisionaría con esa clave.

-- 'Aguardiente amarillo' (game_id=2)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Aguardiente Amarillo, una marca icónica de tradición colombiana que conecta a las personas a través de experiencias de celebración y cercanía. Nos caracterizamos por nuestra autenticidad, herencia cultural y capacidad de reunir generaciones alrededor de momentos memorables.', 'Aguardiente amarillo', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"levels\": [], \"power_ups\": [], \"ball_speed\": 50, \"brick_rows\": 5, \"paddle_speed\": 350, \"brick_columns\": 8, \"url_ball_texture\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/07baa430-ef09-4704-aeeb-020918c067f7.png\", \"url_paddle_texture\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/8bbc131e-d1df-40d8-a3f7-5dd9992e8d01.png\"}, \"meta\": {\"brand_id\": \"aguardiente-amarillo\", \"campaign_id\": \"\"}, \"audio\": {\"hit_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/78f5d717-e3b3-4221-bdb2-3fca7c6a775a.mp3\", \"win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/7f42849b-9e1f-4e4b-bbb4-51dd709141f9.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/2e55d595-9973-4994-8456-e93a652c3f6f.mp3\", \"brick_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/d8835c02-a027-4583-9677-d7d9f2be629a.mp3\", \"laser_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/e158d354-21ed-48a0-9ddf-e683c33f08eb.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/f1c55962-975a-4f9a-a611-cf23af25cbb1.mp3\", \"powerup_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/c41ac716-f03d-465b-bd5e-91755fc8978e.mp3\"}, \"texts\": {\"defeat_title\": \"¡Faltó guaro!\", \"defeat_phrase\": \"Se nos acabó la fiesta muy pronto. ¡Pide otro trago y vuelve a intentarlo!\", \"victory_title\": \"¡Salud!\", \"floating_words\": [\"¡Salud!\", \"Sabor\", \"Amarillo\", \"Pionero\"], \"victory_phrase\": \"¡Rompiste todo el tablero! Celebra tu victoria con el pionero de los aguardientes.\", \"floating_color_hex\": \"#E31837\", \"floating_font_size\": 42, \"show_particles_with_words\": true}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 200}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ffa31518-b2f4-4345-b2df-29673dd1f15e.webp\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ce853a1c-7be8-4813-b291-f31168ddb5d8.png\", \"main_image_offset_y\": 1, \"logo_watermark_offset_y\": 1}, \"shader_background_config\": {\"Back\": {\"Enabled\": true, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/59f2574c-9c05-418c-846d-eaeb02900fd6.jpg\"}, \"Front\": {\"Enabled\": true}, \"Speed\": 0.6, \"UseShader\": true, \"Difficulty\": 1, \"PrimaryColorHex\": \"#FFCC00\", \"ParticleColorHex\": \"#008000\", \"SecondaryColorHex\": \"#E6B800\"}}, \"game_config\": {\"difficulty\": \"Medium\", \"initial_lives\": 3, \"score_per_hit\": 50, \"game_duration_sec\": 90}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://ilc.com.co/marca/aguardiente-amarillo', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 2,
    (SELECT id FROM game_config_definitions WHERE game_id = 2 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 2 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Aguardiente amarillo'
);

-- 'Éxito' (game_id=4)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Éxito, una marca líder del comercio retail en Colombia comprometida con ofrecer una experiencia de compra integral, accesible y cercana. A través de nuestra amplia oferta de productos, servicios y beneficios, buscamos mejorar la calidad de vida de nuestros clientes y fortalecer nuestro impacto positivo en las comunidades donde estamos presentes.', 'Éxito', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"objects\": [{\"id\": \"manzana\", \"scale\": 2.5, \"score\": 50, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/af44ce5b-e4ba-4949-84c1-90d91ced0f92.png\"}, {\"id\": \"leche\", \"scale\": 2.5, \"score\": 50, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0859ddb5-0ebb-422e-b3b5-8f45f47fa378.png\", \"is_obstacle\": false}, {\"id\": \"pan\", \"scale\": 2.5, \"score\": 50, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1d4abffd-5eee-45ea-abfc-759917005a61.png\", \"is_obstacle\": false}, {\"id\": \"trash\", \"scale\": 2.5, \"score\": -20, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5f74f503-ae79-4087-be77-69dd75d4deaf.png\", \"is_obstacle\": true}], \"basket_speed\": 15, \"max_quantity\": 5, \"min_quantity\": 2, \"fall_speed_max\": 4, \"fall_speed_min\": 2, \"basket_sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/16b3cd44-c49d-4a3a-a2e3-4bc28f0d6813.png\", \"list_bg_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0d75dffe-8127-400f-8937-e9614d7e5418.png\", \"shopping_list_size\": 3}, \"meta\": {\"brand_id\": \"exito\", \"campaign_id\": \"\"}, \"audio\": {\"music_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"spawn_url\": \"https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg\", \"negative_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"positive_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\"}, \"texts\": {\"defeat_title\": \"HAS PERDIDO\", \"defeat_phrase\": \"Inténtalo de nuevo.\", \"victory_title\": \"¡HAS GANADO!\", \"victory_phrase\": \"¡Has recogido todos los objetos de la lista!\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/89795915-8bef-466f-b782-2f3dac136bc4.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/3b0a529e-d8a6-4089-b8d4-fd1b0ea71c48.webp\"}, \"shader_background_config\": {\"Back\": {\"Alpha\": 1, \"Speed\": 0, \"Tiling\": {\"x\": 1, \"y\": 1}, \"ColorHex\": \"#ffffff\", \"Rotation\": 0, \"Direction\": {\"x\": 0, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7a69d38c-6c11-4856-90f9-a215a557cfcd.webp\"}, \"Front\": {\"Alpha\": 0, \"Speed\": 0.2, \"Tiling\": {\"x\": 1, \"y\": 1}, \"Enabled\": false, \"ColorHex\": \"#fee502\", \"Rotation\": 0, \"Direction\": {\"x\": 1, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/eea60b88-3f7e-4453-bf76-0e37b64e53e2.png\"}}}, \"game_config\": {\"lives\": 3, \"duration\": 60, \"spawn_rate\": 1.2}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://www.exito.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 4,
    (SELECT id FROM game_config_definitions WHERE game_id = 4 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 4 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Éxito'
);

-- 'Drogueria la rebaja' (game_id=5)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Droguerías La Rebaja, una marca dedicada a facilitar el acceso a productos y servicios relacionados con la salud, el bienestar y el cuidado personal. Nos caracterizamos por nuestra cercanía, cobertura y compromiso con ofrecer soluciones confiables que contribuyan al bienestar de las comunidades a las que servimos.', 'Drogueria la rebaja', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"words\": [{\"hint\": \"El servicio que te lleva las medicinas hasta la puerta de tu casa.\", \"word\": \"DOMICILIOS\", \"score\": 100}, {\"hint\": \"El estado de equilibrio y salud que siempre queremos proteger.\", \"word\": \"BIENESTAR\", \"score\": 100}, {\"hint\": \"El nombre de tu droguería de confianza en toda Colombia.\", \"word\": \"LA REBAJA\", \"score\": 200}], \"font_color_hex\": \"#FFFFFF\", \"power_ups_config\": [{\"cost\": 50, \"type\": \"RevealLetter\", \"icon_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1ab95c11-8a49-45cc-a2a5-7e0e8883eeaf.png\", \"color_hex\": \"#ffffffff\", \"display_name\": \"Hint\"}, {\"cost\": 30, \"type\": \"ZapOptions\", \"icon_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d3e4c776-8b4e-4e7c-94ff-8b611e762921.png\", \"color_hex\": \"#ffffffff\", \"display_name\": \"Zap\"}, {\"cost\": 100, \"type\": \"ExtraLife\", \"icon_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/18437170-ad14-461f-872f-e12f83d1d5a1.png\", \"color_hex\": \"#ffffffff\", \"display_name\": \"Life\"}], \"hangman_progress_urls\": [\"https://cdn.verygana.com/public/campaigns/designer-2/image/4688e57a-2503-460a-9120-bf318b282bf4.png\"]}, \"meta\": {\"brand_id\": \"drogueria-la-rebaja\", \"campaign_id\": \"\"}, \"audio\": {\"click_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/405564a8-bda7-4ebc-8ecd-c962c7e39918.mp3\", \"music_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"zap_sound_url\": \"https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg\", \"life_sound_url\": \"https://rtainor.com/verygana/musicmemory/windows_xp.mp3\", \"defeat_sound_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"reveal_sound_url\": \"https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg\", \"victory_sound_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\"}, \"texts\": {\"defeat_title\": \"GAME OVER\", \"defeat_phrase\": \"Try Again\", \"victory_title\": \"VICTORY!\", \"victory_phrase\": \"Congratulations! You Won!\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/555162b8-2738-45e3-8c13-290819963861.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/865efce3-3728-4b34-b35c-7b250d7285f3.png\", \"keyboard_sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ebd5ff89-4312-4c99-9e13-1c21ecc2fcb2.png\", \"main_image_offset_y\": 0, \"logo_watermark_offset_y\": 0}, \"shader_background_config\": {\"Back\": {\"Alpha\": 1, \"Speed\": 0.2, \"Tiling\": {\"x\": 1, \"y\": 1}, \"ColorHex\": \"#ffffff\", \"Rotation\": 0, \"Direction\": {\"x\": 1, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e964c261-61a3-49c6-9a9d-a567fc030e58.png\"}, \"Front\": {\"Alpha\": 0, \"Speed\": 0.5, \"Tiling\": {\"x\": 1, \"y\": 1}, \"ColorHex\": \"#FFFFFF00\", \"Rotation\": 0, \"Direction\": {\"x\": 1, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1e26c8df-884e-4657-9d25-e34b59147056.png\"}}}, \"game_config\": {\"difficulty\": \"normal\", \"time_limit\": 90, \"max_attempts\": 6}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://www.larebajavirtual.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 5,
    (SELECT id FROM game_config_definitions WHERE game_id = 5 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 5 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Drogueria la rebaja'
);

-- 'Ara' (game_id=6)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Ara, una cadena de tiendas de proximidad comprometida con ofrecer productos de calidad a precios competitivos. Nos enfocamos en brindar una experiencia de compra cercana, accesible y eficiente, contribuyendo al bienestar de nuestros clientes y al desarrollo de las comunidades donde operamos.', 'Ara', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"tiles\": [{\"id\": 1, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/aa2b2b8e-0893-43b6-aef5-ff3a228bebc6.png\"}, {\"id\": 2, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/25d0fc8a-632d-4b2a-9f75-c3bd07592f10.png\"}, {\"id\": 3, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/3adea538-417d-421e-ac86-3ad3bc90069b.png\"}, {\"id\": 4, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cbbcbb74-c049-4a43-9041-1cf4f7797507.png\"}, {\"id\": 5, \"score\": 15, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/56f2a4de-b423-4c1d-a0b0-d5c10eace4c3.png\"}, {\"id\": 6, \"score\": 15, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cf3c0baf-08be-472a-b2f0-6373cda0d32c.png\"}]}, \"meta\": {\"brand_id\": \"ara\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/55b471fb-4d35-4a8b-9a25-e974ee74b4f7.mp3\", \"swap_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8f75f8c2-d98c-4bec-b488-6fc9ae10ef2d.mp3\", \"match_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/53e57d6d-d388-4757-a5eb-d86db41d91df.mp3\", \"music_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/9a703273-d347-4373-9dfe-7e47f7fa4407.mp3\"}, \"texts\": {\"defeat_title\": \"¡Faltó ahorro!\", \"defeat_phrase\": \"Se te escaparon algunas ofertas. ¡Vuelve a jugar y llena tu carrito con los mejores precios de Ara!\", \"victory_title\": \"¡Alegría al mejor precio!\", \"victory_phrase\": \"¡Eres el rey del ahorro! Combinaste todas las ofertas y ganaste. Sigue disfrutando de la alegría de comprar en Ara.\"}, \"rewards\": {\"coins_per_action\": 10, \"combo_multiplier\": 0.1, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0d521b9f-5d5f-49cf-9aaa-2eee4d3ca366.png\", \"background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ff7e0046-ae3f-41be-9c0d-b4c82b1ef931.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/9c4868a7-e4aa-4821-bd9c-01c25b7255dd.png\", \"main_logo_offset_y\": 0, \"grid_background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c99f5c0d-9e0d-4e56-bdaa-77dd06711ab1.png\", \"background_color_hex\": \"#FFD100\", \"logo_watermark_offset_y\": 0, \"grid_background_color_hex\": \"#E30613\"}, \"background_config\": {\"Back\": {\"Speed\": 0.05, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0e60381e-a81f-4792-9b6f-c3d0bc51c8c3.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.2, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ba5fd231-9615-4b92-8804-35d96c6c8c03.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"difficulty\": \"normal\", \"time_limit\": 60, \"max_attempts\": 3, \"target_tiles\": 10, \"total_levels\": 5}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://aratiendas.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 6,
    (SELECT id FROM game_config_definitions WHERE game_id = 6 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 6 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Ara'
);

-- 'D1' (game_id=7)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Tiendas D1, una cadena de descuento comprometida con ofrecer productos de calidad a precios accesibles. A través de un modelo de compra eficiente y cercano, buscamos simplificar la experiencia de nuestros clientes y contribuir al bienestar de las familias, brindando alternativas de ahorro y confianza en cada visita.', 'D1', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"card_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d2db689a-f94a-4285-9f55-d19273f5937e.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fc93af66-e509-4b1c-906b-6dbe94ed4986.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e2dad50b-ad34-40c2-998e-1b09c94ba759.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/270c17b4-82e9-4cfe-8ace-54e37b7b948d.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/07be91c2-39ae-4fbb-b244-66af77db0457.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7e98dcf7-b9dd-4565-898a-daafefc3dd9d.png\"}]}, \"meta\": {\"brand_id\": \"d1\", \"campaign_id\": \"\"}, \"audio\": {\"flip_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/25934438-68d9-4cec-b9bc-73069ec76f73.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/f6185748-0c53-4c78-a8d2-444988a776d5.mp3\", \"error_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8076a72b-b625-48b7-ab3d-19ab050f7d9a.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/d9b61641-7743-4646-93e6-a4d01880a5a9.mp3\", \"powerup_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/772a2987-2991-4c1f-ab73-3d01f2a152b5.mp3\", \"success_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/96d031dc-ff7c-43d5-a758-a2fded25ce7a.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5566a321-b3b8-41a7-b3a1-c7b9f3a52e90.mp3\"}, \"texts\": {\"label_time\": \"Tiempo\", \"label_level\": \"Nivel\", \"label_score\": \"Llaves\", \"defeat_title\": \"¡Faltaron productos!\", \"defeat_phrase\": \"No lograste completar tu carrito a tiempo. ¡Concéntrate y vuelve a intentarlo!\", \"victory_title\": \"¡Excelente mercado!\", \"label_attempts\": \"Intentos\", \"victory_phrase\": \"¡Encontraste todas las parejas! Llevas la mejor calidad a los mejores precios con D1.\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"card_back_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/8515c0e7-d564-484c-b46e-9f57b35a3e34.png\", \"icon_hint_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/61ca4a2f-2b4a-42f8-a768-d16ea26bd651.png\", \"icon_life_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/67bf1d0d-170f-4a0c-bbae-6b048b0e531b.png\", \"icon_time_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a05d76a1-dca7-4672-90b7-e45217b80c12.png\", \"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/493ff24e-fba1-41e3-bf4c-6766f9dfecaf.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1f50e91f-7b0c-4dad-81c1-805ad4bb8b15.png\", \"main_logo_offset_y\": 0, \"logo_watermark_offset_y\": 0}, \"background_config\": {\"Back\": {\"Speed\": 0.05, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f926e40a-f5ff-4e90-95ac-66594ce8b1b8.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.2, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/baa4b645-e4dc-4b17-a218-176ac46d722e.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"levels\": [], \"max_lives\": 3, \"final_pairs\": 8, \"start_pairs\": 3, \"enable_lives\": true, \"game_duration\": 90, \"bonus_per_pair\": 5, \"enable_powerups\": true, \"power_up_chance\": 0.2, \"preview_duration\": 3, \"bonus_time_amount\": 10}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://www.d1.com.co/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 7,
    (SELECT id FROM game_config_definitions WHERE game_id = 7 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age IS NULL AND max_age IS NULL AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 7 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'D1'
);

-- 'La fogata' (game_id=8)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos La Fogata, una marca gastronómica comprometida con ofrecer experiencias culinarias que combinan sabor, tradición y hospitalidad. Nos enfocamos en brindar una atención cercana y una propuesta gastronómica de calidad, convirtiéndonos en un lugar ideal para disfrutar y compartir en cualquier ocasión.', 'La fogata', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"tiles\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f35ff6a9-2d10-44f6-85fe-d33aa7679034.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a8e51804-dedc-4bac-b1a9-2f35b1a0ad76.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a979ea3a-2726-4eda-b0d1-1b91380e2099.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/6f851967-0b24-4313-abd7-e4f24ed3febc.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f18157b9-3579-4799-9203-c239ebbd0868.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/487d90b2-f925-4c41-80fd-d7ae38827629.jpg\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/06b4f16c-27ed-4bdf-a676-a0b8f9c4bc5e.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ac1b0b41-f404-4ded-9074-84cb59fde58a.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/bc717b61-c5c1-401c-838c-634757cbae22.png\"}]}, \"meta\": {\"brand_id\": \"la-fogata\", \"campaign_id\": \"\"}, \"audio\": {\"bomb_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/87635d2b-d0c7-407e-958a-c27185581f1c.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/6e810e09-3465-4436-8687-ab1ed3c91bdd.mp3\", \"click_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/927be6ea-ae7f-4b66-99d5-ccfff7c75b93.mp3\", \"error_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8e4a401e-95b4-47bb-993e-305df1fb35bd.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5facf21d-36f6-4820-8dd8-354c9e56eadc.mp3\", \"rocket_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/23a8d057-267c-4e70-812d-961c97746eec.mp3\", \"whoosh_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/27a0a4db-91da-48d2-a138-26270ec92fb5.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/22c50af6-a604-4046-846e-03390d68be5d.mp3\", \"win_game_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/6fb0c1e9-1894-4a6d-93ac-07cd264b1487.mp3\"}, \"texts\": {\"label_time\": \"Fuego:\", \"label_score\": \"Sabor:\", \"defeat_title\": \"¡Se quemó el asado!\", \"label_errors\": \"Quemados:\", \"defeat_phrase\": \"La carne se pasó de término. ¡Vuelve a prender el fuego!\", \"victory_title\": \"¡Asado Perfecto!\", \"victory_phrase\": \"¡Eres el maestro parrillero! Todo quedó en su punto exacto.\", \"label_difficulty\": \"Término:\"}, \"rewards\": {\"coins_per_action\": 20, \"coins_on_completion\": 200}, \"branding\": {\"colors\": {\"btn_bg_hex\": \"#D9534F\", \"cell_bg_hex\": \"#FFF8DC\", \"grid_bg_hex\": \"#8B4513\", \"selected_hex\": \"#F0AD4E\", \"text_fixed_hex\": \"#333333\", \"unselected_hex\": \"#FFF8DC\", \"text_normal_hex\": \"#D9534F\"}, \"images\": {\"bomb_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fd17bc82-d600-4e7b-aa0c-48384e5f106d.png\", \"vertical_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fbe5f038-5c2d-4b87-825e-28e0f745182f.png\", \"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/2b6ea6fc-bbb0-4311-8fc5-355b662c6e86.png\", \"background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0135572f-4d0d-44ef-a8f1-07be7726e352.png\", \"horizontal_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/22d893c8-2593-454e-8456-7d5f12a0901d.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/506c3df6-30e6-4615-9391-a671e93d2e41.png\", \"main_logo_offset_y\": 0, \"cell_background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/4b88ddc2-297d-4754-9252-d63b59d87b23.png\", \"background_color_hex\": \"#2C1E16\", \"button_background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/02f4bc4d-4512-443a-b2bf-799131dbc1db.png\", \"logo_watermark_offset_y\": 0}, \"background_config\": {\"Back\": {\"Speed\": 0.05, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d72973af-eba4-45dc-af51-78568b932eb4.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.2, \"Enabled\": false, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/81416559-2e80-4598-b447-506c1b56763c.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"levels\": [{\"id\": 2, \"max_errors\": 3, \"time_limit\": 300, \"empty_cells\": 12, \"use_countdown\": true, \"enable_powerups\": true}], \"difficulty\": \"normal\", \"max_errors\": 3, \"time_limit\": 300, \"empty_cells\": 12, \"use_countdown\": true, \"enable_powerups\": true, \"warning_threshold\": 0.15}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://restaurante-la-fogata-2.ola.click/products', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 8,
    (SELECT id FROM game_config_definitions WHERE game_id = 8 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 8 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'La fogata'
);

-- 'Don pollo' (game_id=9)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Don Pollo, una marca especializada en productos avícolas que combina calidad, experiencia y cercanía para ofrecer alternativas alimenticias confiables y accesibles. Nos enfocamos en satisfacer las necesidades de nuestros consumidores a través de productos frescos, prácticas responsables y un servicio orientado al bienestar de las familias.', 'Don pollo', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"meta\": {\"brand_id\": \"don-pollo\", \"campaign_id\": \"\"}, \"audio\": {\"coin_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/17d4df8a-3cc2-4384-aa6d-26b0cd8f629b.mp3\", \"jump_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/2e09743d-9215-4f30-a035-671bf91fa0e3.mp3\", \"land_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/fc9618c1-20a1-4fdb-9693-ec8a2bdfd5aa.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/3aa8fcfa-577e-4ad7-8a3b-aeba33a2d4a0.mp3\", \"death_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/9b2789c6-fcf5-4109-a512-a8a14d1ae80e.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/af9bf4fd-6a0e-4ef9-89c7-4a49cec1bdd8.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8570d3a6-d676-400c-88eb-24e0a108c61a.mp3\"}, \"texts\": {\"label_time\": \"TIEMPO\", \"label_score\": \"LLAVES\", \"defeat_title\": \"¡Tropiezo!\", \"label_record\": \"RÉCORD\", \"defeat_phrase\": \"Te enredaste en el camino. ¡Vuelve a intentarlo y llega más lejos!\", \"victory_title\": \"¡Gran Carrera!\", \"victory_phrase\": \"¡Llegaste a la meta esquivando todos los obstáculos con éxito! Disfruta tu premio.\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b005a92c-fdae-495c-a222-61376fa368db.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0075a4ac-2ed3-4e05-9d6d-55db889ecd7c.png\", \"main_logo_offset_y\": 50, \"logo_watermark_offset_y\": -50}, \"visuals\": {\"coin_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a90a5c75-a1e8-476b-90ad-76cd9bb77421.png\", \"coin_scale\": 1.2, \"ground_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/3946274b-efdd-418b-ab7e-b4555cad18bb.jpg\", \"player_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/707d17f1-d231-4929-85f3-d37d879897f6.png\", \"dw_bg_color\": \"#220000\", \"dw_scroll_x\": 0.5, \"dw_scroll_y\": 0.5, \"air_trap_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e8a09db2-e69e-4523-aabc-4a4c6247336f.png\", \"bg_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e9fa4b51-06b2-4e01-81be-0aa84a2995ca.jpg\", \"ground_color\": \"#ffffff\", \"dw_desphase_x\": 0.1, \"dw_desphase_y\": 0.1, \"air_trap_scale\": 1, \"bg_solid_color\": \"#1a0b2e\", \"death_wall_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d214a8ae-34d0-4e7d-ac4a-123372fedeeb.png\", \"ground_trap_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/554d9bc7-e271-41f6-9d4b-cce2c4cc92ec.png\", \"bg_texture_scale\": 1, \"dw_primary_color\": \"#ff0000\", \"ground_trap_scale\": 0.9, \"dw_secondary_color\": \"#550000\", \"ground_texture_scale\": 1}, \"background_config\": {\"Back\": {\"Speed\": 0, \"Enabled\": true, \"ColorHex\": \"#0A0A1E\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c970230b-3d2e-4fcc-890d-6666d48ef473.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.1, \"Enabled\": true, \"ColorHex\": \"#FFFFFF80\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7f731003-0794-47c3-8ec2-65a461a8b4bb.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"max_lives\": 3, \"jump_force\": 12, \"acceleration\": 0.05, \"scroll_speed\": 4, \"game_duration\": 60, \"gravity_scale\": 3, \"use_countdown\": true, \"max_scroll_speed\": 10}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://donpollo.co/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 9,
    (SELECT id FROM game_config_definitions WHERE game_id = 9 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 9 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Don pollo'
);

-- 'Poker' (game_id=10)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Poker, una marca colombiana que promueve la conexión entre las personas a través de experiencias auténticas de compartir. Nuestro propósito es acompañar los momentos de amistad y celebración, construyendo relaciones duraderas con nuestros consumidores a partir de la cercanía, la confianza y el espíritu de comunidad.', 'Poker', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"hitColorHex\": \"#FFFFFF\", \"hitSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/88573fb6-fed4-4fc3-8abc-edefa2c935f1.jpg\", \"holeColorHex\": \"#FFFFFF\", \"moleColorHex\": \"#FFFFFF\", \"errorColorHex\": \"#FFFFFF\", \"holeSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/59b9475d-57f1-4210-afb5-742f247c2edb.png\", \"moleSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c32c0c25-4c99-42bd-a38b-7c773a822d66.jpg\", \"errorSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d04f436f-88d2-431f-8334-b109f1d6fff1.png\", \"showPreviewButtons\": true, \"previewButtonColorHex\": \"#FFFFFF\", \"previewButtonSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/43535fcb-a77c-4062-b5ff-7cd18b57957c.png\"}, \"meta\": {\"brand_id\": \"poker\", \"campaign_id\": \"\"}, \"audio\": {\"win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/bf580665-3d9f-4213-95a7-2c2d52d97d51.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5068bca3-e2cb-4701-aaca-9672f0012d9e.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/2d7dd1cd-67e3-4304-9fd8-f3b18cd0a218.mp3\", \"hit_sfx_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/b5b5af15-a432-4d16-89b2-71de80328aa1.mp3\", \"miss_sfx_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/7ab90f37-5227-480d-8d91-57f052c3ef94.mp3\", \"main_audio_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5acb4d4e-e836-4631-a90f-f852bf2d09e5.mp3\"}, \"texts\": {\"defeat_title\": \"¡Se te enfriaron!\", \"defeat_phrase\": \"Se te escaparon demasiadas botellas de las manos. ¡Vuelve a intentarlo y demuestra tus reflejos!\", \"victory_title\": \"¡Amigos y Polas!\", \"victory_phrase\": \"¡Atrapaste todas las botellas a tiempo! El parche está asegurado y la diversión no para.\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/53117176-5df7-40f3-b5af-6a66571badc6.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e3167dd2-e5f5-4455-b9cd-4ccf4eb5042b.png\"}, \"shader_background_config\": {\"Back\": {\"Alpha\": 1, \"ColorHex\": \"#FFFFFF\", \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/304d5c7c-6ccf-436c-be73-cb4ef67d185b.png\"}, \"Front\": {\"Enabled\": false, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/67773ad2-913f-4c28-8565-90a4c2fc46e6.png\"}}}, \"game_config\": {\"duration\": 60, \"gridCols\": 3, \"gridRows\": 3, \"maxLives\": 3, \"moleLifetime\": 2, \"pointsPerHit\": 10, \"spawnInterval\": 0.8, \"audioDivisions\": 3}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://www.cervezapoker.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 10,
    (SELECT id FROM game_config_definitions WHERE game_id = 10 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 10 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Poker'
);

-- 'Unicentro' (game_id=11)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Unicentro, una marca cercana, confiable y dinámica que crea experiencias integrales para sus visitantes. Nuestro compromiso es ser el punto de encuentro preferido de la comunidad, ofreciendo una propuesta que combina comercio, entretenimiento, innovación y bienestar, convirtiendo cada visita en una experiencia memorable.', 'Unicentro', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"character_color\": \"#ffffff\", \"background_phrases\": [\"El poder de hacer lo posible\", \"Alcanza tus metas financieras\", \"Ahorrar es tu mejor jugada\", \"Tu plata segura desde la App\"], \"character_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5e815ad0-1f9e-4ed5-9d69-dc258ef7a686.jpg\", \"key_spawn_probability\": 0.3}, \"meta\": {\"brand_id\": \"unicentro\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/828022f8-6177-4fef-9dce-596b4311f5bc.mp3\", \"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/b0aa57e0-3e39-4d6c-9858-9ee3041cbd93.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Cuidado con los gastos hormiga! Cuida tu plata y vuelve a intentarlo.\", \"Un pequeño tropiezo financiero. ¡Levántate y sigue corriendo hacia tus metas!\", \"¡Cuidado con los gastos hormiga! Cuida tu plata y vuelve a intentarlo.\", \"Tus sueños te siguen esperando. ¡Revisa tu estrategia y juega de nuevo!\"]}, \"rewards\": {\"keys_per_action\": 10}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7f10ee33-c853-40d1-aa3b-8ae945f93cc4.jpg\"}, \"game_config\": {}}', 3, NOW(),
    'CAMPAIGN_CREATED', 'https://unicentrodearmenia.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 11,
    (SELECT id FROM game_config_definitions WHERE game_id = 11 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 11 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Unicentro'
);

-- 'Netflix' (game_id=12)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Netflix, una compañía líder en entretenimiento digital que ofrece experiencias audiovisuales innovadoras a una audiencia global. A través de contenido original y una amplia variedad de historias, buscamos entretener, conectar e inspirar a millones de personas alrededor del mundo.', 'Netflix', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"character_id\": -1, \"wood_sign_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/36e8a1dd-f651-422c-aac1-d22c76fa80e4.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b2717f78-52cb-4205-acce-0c769b949e9f.png\"}], \"wood_sign_phrases\": [\"¿Sigues ahí?\", \"Solo un capítulo más\", \"Spoiler Alert!\", \"Alerta de Maratón\"], \"key_spawn_probability\": 0.3}, \"meta\": {\"brand_id\": \"netflix\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5e109b52-4abe-4a48-900a-71ee9b470de5.mp3\", \"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/4e502807-4d92-4ee0-bbec-c9bfcac1af8c.mp3\"}, \"texts\": {\"game_over_messages\": [\"¿Sigues ahí? ¡Dale a reiniciar para continuar tu maratón!\", \"Fin de temporada. ¡Corre por una nueva oportunidad!\", \"Se cortó el internet. ¡Vuelve a intentarlo!\"]}, \"rewards\": {\"keys_per_action\": 5}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5e008ffc-f341-491a-bff3-a02a0b10292e.png\"}, \"game_config\": {}}', 3, NOW(),
    'CAMPAIGN_CREATED', 'https://www.netflix.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 12,
    (SELECT id FROM game_config_definitions WHERE game_id = 12 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 12 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Netflix'
);

-- 'Colgate' (game_id=13)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Colgate, una marca líder en el cuidado bucal dedicada a desarrollar productos respaldados por la ciencia y la innovación. Trabajamos para fomentar hábitos saludables y brindar soluciones eficaces que contribuyan al bienestar de millones de personas, ayudándolas a cuidar su sonrisa con confianza todos los días', 'Colgate', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"card_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f335e101-8614-4445-b317-f898f0d508b0.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cbd935bb-7a80-4a7c-9705-59b8558c1555.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/45e1d103-95a9-4516-8e91-0deae760724a.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/4ce7c4ca-bbc3-47e8-a9b4-b68b9e37aa14.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f9e0f345-174c-4290-8330-22c446680d34.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/6da6e5ae-22f8-43de-a5ec-2e61e18475c9.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d599ce76-5a7d-4a30-8e4b-d9c99699b8ae.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b23b35de-af0f-4a91-b78b-75deba3bb578.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/8f1791c5-5a5d-4f61-ae75-d1baf1b05e1e.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/87a032c6-1be6-4e39-b806-5efe57959bab.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cd37a1d9-7c15-4bdc-9249-cf85c7dfdf44.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c5d3c5ef-9d14-4ef9-8811-7f4e5962adcf.png\"}]}, \"meta\": {\"brand_id\": \"colgate\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/76f58f83-ed0a-4031-980e-653858256b6e.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/99cbdf02-1f2e-4629-9a3d-343acb4eac8f.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Sonrisa brillante! Encontraste todas las parejas.\", \"¡Memoria 100% libre de caries! Eres un experto en frescura.\", \"¡Misión cumplida! Tu memoria es tan fuerte como tus dientes.\", \"¡Protección total! Ganaste el reto Colgate.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 100}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/47fb34c8-6a5c-488d-80a2-fd7ff6adbc5f.jpg\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/55738786-cf06-4e74-8963-028a7f5ff239.jpg\"}, \"game_config\": {}}', 3, NOW(),
    'CAMPAIGN_CREATED', 'https://www.colgate.com/en-us', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 13,
    (SELECT id FROM game_config_definitions WHERE game_id = 13 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 13 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Colgate'
);

-- 'Claro' (game_id=14)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Claro, una marca que brinda soluciones de conectividad y comunicación a través de servicios móviles, internet, televisión y tecnología digital. Nos enfocamos en ofrecer una experiencia confiable, accesible e innovadora, ayudando a las personas y empresas a mantenerse conectadas en todo momento.', 'Claro', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"rock_logos\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/789959c4-0cff-4f81-97db-735a23746723.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/2a514278-3891-4066-a00a-8f4d76308af1.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/4876bc0b-d035-41a3-b2a0-64aadbac4b4b.png\"}], \"plane_words\": [\"Velocidad Total\", \"Máxima Señal\", \"Conexión Pro\", \"Todo Ilimitado\", \"Red Más Rápida\"], \"character_id\": -1, \"character_colors\": [\"#da291c\", \"#FFFFFF\", \"#000000\"], \"key_spawn_probability\": 0.35}, \"meta\": {\"brand_id\": \"claro\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8a04123d-bd75-43ed-8550-b49c7a1aaf44.mp3\", \"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/7bb1b6b1-27ce-49f2-9953-0becac9a3426.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Te quedaste sin datos para esquivar! Reinicia tu partida.\", \"¡Se cayó la conexión en tu carrera! Vuelve a intentarlo.\", \"Encontramos un obstáculo en la red. ¡Dale a jugar otra vez!\"]}, \"rewards\": {\"keys_per_action\": 10}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d2be40e9-9215-472c-9f9b-1d1e069e565b.png\"}, \"game_config\": {}}', 3, NOW(),
    'CAMPAIGN_CREATED', 'https://www.claro.com/?ref=logo', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 14,
    (SELECT id FROM game_config_definitions WHERE game_id = 14 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 14 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Claro'
);

-- 'Sura' (game_id=15)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos SURA, una marca que acompaña a las personas y organizaciones en la gestión de riesgos y el cuidado de su bienestar. Ofrecemos soluciones en salud, seguros y servicios financieros con un enfoque basado en la confianza, la cercanía y la sostenibilidad, contribuyendo a una mejor calidad de vida y al desarrollo de la sociedad.', 'Sura', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"entries\": [{\"clue\": \"Bienestar físico y mental que cuidamos todos los días.\", \"word\": \"SALUD\"}, {\"clue\": \"Respaldo y protección financiera ante cualquier imprevisto.\", \"word\": \"SEGURO\"}, {\"clue\": \"Estado de equilibrio y calidad de vida para las personas.\", \"word\": \"BIENESTAR\"}, {\"clue\": \"Lo construimos hoy con planeación y tranquilidad.\", \"word\": \"FUTURO\"}, {\"clue\": \"Práctica financiera para alcanzar tus metas a largo plazo.\", \"word\": \"AHORRO\"}, {\"clue\": \"Lo más valioso que acompañamos a proteger en cada etapa.\", \"word\": \"VIDA\"}, {\"clue\": \"Cobertura integral para ti y tu familia ante riesgos.\", \"word\": \"PROTECCION\"}, {\"clue\": \"El lazo de seguridad que generamos contigo cada día.\", \"word\": \"CONFIANZA\"}, {\"clue\": \"Análisis del entorno para anticiparnos al mañana.\", \"word\": \"TENDENCIAS\"}, {\"clue\": \"Cuidado anticipado para cuidar tu salud y bienestar.\", \"word\": \"PREVENCION\"}]}, \"meta\": {\"brand_id\": \"sura\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/70d76262-32f7-4ace-88fc-5598231ac13c.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/f84e8256-c8d6-433f-9e53-f59aee1bbfe7.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Excelente trabajo! Tu mente y tu bienestar van de la mano.\", \"¡Completaste el reto! Así de claro es construir un futuro seguro con Sura.\", \"¡Lo lograste! Cuidar de ti también es un juego de concentración.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b3ce4c00-f443-4a0f-b4c3-1232a84c2a52.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7d2e0964-0571-44bf-aa52-4f47020eff8c.png\"}, \"game_config\": {}}', 3, NOW(),
    'CAMPAIGN_CREATED', 'https://www.epssura.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 15,
    (SELECT id FROM game_config_definitions WHERE game_id = 15 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 15 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Sura'
);

-- 'Postobon' (game_id=16)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Postobón, una marca que conecta a las personas mediante experiencias de consumo cercanas, refrescantes y memorables. Nos caracterizamos por nuestra tradición, capacidad de innovación y compromiso con ofrecer bebidas que forman parte de los momentos más importantes de nuestros consumidores.', 'Postobon', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"container_colors\": [\"#ee4c98\", \"#ee4c98\", \"#0f62a0\"], \"container_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a77423df-e9d1-415f-8438-cbb03eb5c58a.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/65d9fc21-cf95-45af-a8d0-88dd29cb2b19.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cec4068f-92ab-41ee-9d1a-ab655ba5dae5.png\"}], \"containers_per_key\": 4}, \"meta\": {\"brand_id\": \"postobon\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5482492c-c7c0-4993-b7cd-4cebe2f46454.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/05613c1f-e300-4c5d-9692-62f4f9b42ab7.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Se nos acabó el gas! Tómate un respiro y vuelve a jugar.\", \"¡Te faltó un sorbo para ganar! Destapa una nueva oportunidad.\", \"¡La sed de victoria sigue intacta! Reinicia y refresca tu puntaje.\"]}, \"rewards\": {\"keys_per_action\": 15}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a20e7752-947c-499f-86cb-9edfd4b527d7.webp\"}, \"game_config\": {}}', 3, NOW(),
    'CAMPAIGN_CREATED', 'https://www.postobon.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 16,
    (SELECT id FROM game_config_definitions WHERE game_id = 16 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 16 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Postobon'
);

-- 'Bancolombia' (game_id=17)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Bancolombia, una marca que conecta a las personas con oportunidades para crecer, progresar y construir un mejor futuro. A través de nuestros productos, servicios y soluciones digitales, impulsamos el bienestar financiero y acompañamos a nuestros clientes en cada etapa de su vida.', 'Bancolombia', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"piece_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/55da063a-de9f-4827-9416-e5aec5eb63ce.png\", \"round_win_phrases\": [\"¡Llenando las bolsas!\", \"¡Encontraste la tienda perfecta!\", \"¡Una compra exitosa!\", \"¡Llevas el mejor estilo!\"]}, \"meta\": {\"brand_id\": \"bancolombia\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/d47574fe-948e-47dc-8180-1e341b64dd08.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/bf14303b-9cb6-477b-ac67-a6bb835e7e01.mp3\", \"game_over_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/e451e25f-4c0a-48c7-b2a9-6220ffdfc2f3.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Uy! Te pesaron mucho las bolsas. ¡Toma un respiro y vuelve a jugar!\", \"Te distrajiste viendo las vitrinas. ¡Concéntrate y atrapa todas las piezas!\", \"¡No te vayas con las manos vacías! Reinicia la ronda y sigue comprando.\"]}, \"rewards\": {\"keys_per_action\": 20, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fafa8507-e3a8-4c90-baa7-ebbe5a79ae15.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/431a80e4-6dcb-4abb-8b61-e0ce33fb4129.png\"}, \"game_config\": {}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://www.bancolombia.com/personas', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 17,
    (SELECT id FROM game_config_definitions WHERE game_id = 17 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 17 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Bancolombia'
);

-- 'Hotel las bailarinas' (game_id=18)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Hotel Las Bailarinas, una marca enfocada en ofrecer experiencias de alojamiento que combinan confort, hospitalidad y contacto con la naturaleza. Nuestro propósito es proporcionar a cada huésped un espacio para descansar, compartir y crear recuerdos inolvidables, destacándonos por nuestro servicio cercano y nuestra atención personalizada.', 'Hotel las bailarinas', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"puzzle_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7e9a6c5e-3318-4e7c-8a81-6686e5a5df27.jpg\"}, \"meta\": {\"brand_id\": \"hotel-las-bailarinas\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/24fb448d-91d0-4cbb-9f86-f91b81632076.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/ff2ac9f4-597b-47d3-baee-f2c296375c5b.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Rompecabezas completado! Tu descanso ideal en la naturaleza te está esperando.\", \"¡Perfecto! Cada pieza encaja tan bien como tus vacaciones soñadas en Las Bailarinas.\", \"¡Lograste armar la diversión! Ahora relájate y prepárate para vivir la experiencia.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5686d840-42fe-4666-bf8b-53937f41e59a.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/91ecd92e-b79b-4706-b7a9-c7f2188d6393.png\"}, \"game_config\": {}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://hotelcampestrelasbailarinas.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 18,
    (SELECT id FROM game_config_definitions WHERE game_id = 18 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 18 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Hotel las bailarinas'
);

-- 'Parque del café' (game_id=19)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Parque del Café, una marca turística y cultural dedicada a preservar, promover y compartir el legado de la cultura cafetera colombiana. A través de experiencias de entretenimiento, educación y aventura, buscamos generar conexión con nuestras raíces y ofrecer momentos inolvidables para visitantes de todas las edades.', 'Parque del café', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"questions\": [{\"id\": 1, \"options\": [\"La caña de azúcar\", \"La cultura cafetera\", \"La historia precolombina\", \"La minería\"], \"question\": \"¿Cuál es la temática principal del Parque del Café?\", \"correct_answer_index\": 1}, {\"id\": 2, \"options\": [\"Salento\", \"Filandia\", \"Montenegro\", \"Quimbaya\"], \"question\": \"¿En qué municipio está ubicado el Parque del Café?\", \"correct_answer_index\": 2}, {\"id\": 3, \"options\": [\"Krater\", \"Cumbre\", \"La Broca\", \"Yippe\"], \"question\": \"¿Cómo se llama la icónica montaña rusa del parque que tiene una caída de 90 grados?\", \"correct_answer_index\": 0}, {\"id\": 4, \"options\": [\"El Tren del Café\", \"El Teleférico\", \"El Helicóptero\", \"La Tirolina\"], \"question\": \"¿Qué medio de transporte panorámico te permite cruzar el parque desde las alturas?\", \"correct_answer_index\": 1}, {\"id\": 5, \"options\": [\"El Secreto de la Montaña\", \"Magia Cafetera\", \"El Show del Café\", \"Danza del Recolector\"], \"question\": \"¿Cuál es el nombre del espectáculo principal que narra la historia del grano en Colombia?\", \"correct_answer_index\": 2}, {\"id\": 6, \"options\": [\"Los Carros Chocones\", \"La Montaña Rusa Yippe\", \"El Paseo a Caballo\", \"El Safari\"], \"question\": \"¿Qué atracción está inspirada en el tradicional Jeep Willys de la región?\", \"correct_answer_index\": 1}, {\"id\": 7, \"options\": [\"La Rueda de la Fortuna\", \"El Faro\", \"El Balcón del Quindío\", \"La Torre Mirador\"], \"question\": \"¿Desde qué estructura puedes tener la mejor vista panorámica de la región cafetera?\", \"correct_answer_index\": 3}, {\"id\": 8, \"options\": [\"La Federación Nacional de Cafeteros\", \"El Ministerio de Cultura\", \"La Gobernación local\", \"Una empresa privada de turismo\"], \"question\": \"¿Qué entidad fundó el Parque del Café en el año 1995?\", \"correct_answer_index\": 0}, {\"id\": 9, \"options\": [\"El Río Lento\", \"Los Rápidos\", \"La Cascada\", \"Botes Chocones\"], \"question\": \"¿Cómo se llama la atracción donde te subes a botes circulares para un recorrido acuático?\", \"correct_answer_index\": 1}, {\"id\": 10, \"options\": [\"El Tranvía\", \"La Carroza\", \"El Tren del Café\", \"El Funicular\"], \"question\": \"¿Qué medio de transporte clásico recorre la parte baja del parque bordeando el bosque?\", \"correct_answer_index\": 2}]}, \"meta\": {\"brand_id\": \"parque-del-cafe\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5b2e512a-bd34-4c67-bab3-f10c1818abb0.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/246f0839-eca1-45c5-a7e3-f5f0b890360e.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Excelente! Conoces el Parque del Café como un verdadero experto de nuestra tierra.\", \"¡Puntaje perfecto! Estás listo para subirte al Yippe y celebrar tu victoria con toda la adrenalina.\", \"¡Qué gran conocimiento! Disfruta de la magia cafetera y sigue sumando llaves en tu cuenta.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/819140df-0980-4f4f-977b-e7649d7a30b5.webp\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/472d0836-df12-49af-83e2-8974a74f37b7.png\"}, \"game_config\": {}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://parquedelcafe.co/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 19,
    (SELECT id FROM game_config_definitions WHERE game_id = 19 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 19 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Parque del café'
);

-- 'Colanta' (game_id=20)
INSERT INTO branding_requests (
    admin_notes, brand_description, brand_name, budget_cents, campaign_goal, created_at,
    draft_form_data, brief_data, end_date, game_config, max_sessions_per_user_per_day, start_date,
    status, target_url, updated_at, assigned_designer_id, campaign_id, commercial_id, game_id,
    game_config_definition_id, reviewed_by_admin_id, target_audience_id
)
SELECT
    NULL, 'Somos Colanta, una marca que conecta el campo y la ciudad a través de alimentos nutritivos, frescos y de calidad. Nuestro propósito es generar valor para nuestros productores, consumidores y comunidades, promoviendo el desarrollo sostenible y el bienestar colectivo mediante un modelo basado en la cooperación y la confianza.', 'Colanta', 1000000, 'BRAND_AWARENESS', NOW(),
    NULL, NULL, NULL, '{\"game\": {\"words\": [{\"word\": \"COLANTA\", \"color\": \"#008A4B\"}, {\"word\": \"LECHE\", \"color\": \"#005AA9\"}, {\"word\": \"QUESO\", \"color\": \"#FFC107\"}, {\"word\": \"CAMPO\", \"color\": \"#27AE60\"}, {\"word\": \"YOGURT\", \"color\": \"#E31837\"}, {\"word\": \"AVENA\", \"color\": \"#E67E22\"}, {\"word\": \"CREMA\", \"color\": \"#00A3E0\"}, {\"word\": \"SUERO\", \"color\": \"#8E44AD\"}, {\"word\": \"FRESCO\", \"color\": \"#00BFFF\"}, {\"word\": \"SABOR\", \"color\": \"#F39C12\"}]}, \"meta\": {\"brand_id\": \"colanta\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/3ac3490f-8c20-4fe0-b51a-7c18b18079f4.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/fd7e58a8-777b-4651-a44d-fd7f06f5006a.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Sabe más! Encontraste todas las palabras y toda la frescura de Colanta.\", \"¡Excelente! Demostraste que tienes el campo y la nutrición en tu mente.\", \"¡Sopa de letras completada! Disfruta del verdadero sabor del campo.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d1d91f06-3c5b-4efc-bb73-b144174ba958.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/24ea7061-75dc-46b4-8891-eb75caeae033.png\"}, \"game_config\": {}}', NULL, NOW(),
    'CAMPAIGN_CREATED', 'https://colanta.com/', NOW(), (SELECT gd.user_id FROM game_designer_details gd JOIN users u ON u.id = gd.user_id WHERE u.email = 'vgmiguel16+designer@gmail.com'), NULL, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), 20,
    (SELECT id FROM game_config_definitions WHERE game_id = 20 AND is_latest = 1), (SELECT ad.user_id FROM admin_details ad JOIN users u ON u.id = ad.user_id WHERE u.email = 'veryganaoficial+admin@gmail.com'), NULL
WHERE NOT EXISTS (
    SELECT 1 FROM branding_requests br
    WHERE br.game_id = 20 AND br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com') AND br.brand_name = 'Colanta'
);

-- Campañas de las 18 marcas (status ACTIVE: cumple el requisito de 'campaña
-- activa'; en el flujo real quedaron en DRAFT porque nadie las publicó).
-- campaign para game_id=2 (brand_id=aguardiente-amarillo)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"levels\": [], \"power_ups\": [], \"ball_speed\": 50, \"brick_rows\": 5, \"paddle_speed\": 350, \"brick_columns\": 8, \"url_ball_texture\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/07baa430-ef09-4704-aeeb-020918c067f7.png\", \"url_paddle_texture\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/8bbc131e-d1df-40d8-a3f7-5dd9992e8d01.png\"}, \"meta\": {\"brand_id\": \"aguardiente-amarillo\", \"campaign_id\": \"\"}, \"audio\": {\"hit_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/78f5d717-e3b3-4221-bdb2-3fca7c6a775a.mp3\", \"win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/7f42849b-9e1f-4e4b-bbb4-51dd709141f9.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/2e55d595-9973-4994-8456-e93a652c3f6f.mp3\", \"brick_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/d8835c02-a027-4583-9677-d7d9f2be629a.mp3\", \"laser_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/e158d354-21ed-48a0-9ddf-e683c33f08eb.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/f1c55962-975a-4f9a-a611-cf23af25cbb1.mp3\", \"powerup_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/c41ac716-f03d-465b-bd5e-91755fc8978e.mp3\"}, \"texts\": {\"defeat_title\": \"¡Faltó guaro!\", \"defeat_phrase\": \"Se nos acabó la fiesta muy pronto. ¡Pide otro trago y vuelve a intentarlo!\", \"victory_title\": \"¡Salud!\", \"floating_words\": [\"¡Salud!\", \"Sabor\", \"Amarillo\", \"Pionero\"], \"victory_phrase\": \"¡Rompiste todo el tablero! Celebra tu victoria con el pionero de los aguardientes.\", \"floating_color_hex\": \"#E31837\", \"floating_font_size\": 42, \"show_particles_with_words\": true}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 200}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ffa31518-b2f4-4345-b2df-29673dd1f15e.webp\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ce853a1c-7be8-4813-b291-f31168ddb5d8.png\", \"main_image_offset_y\": 1, \"logo_watermark_offset_y\": 1}, \"shader_background_config\": {\"Back\": {\"Enabled\": true, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/59f2574c-9c05-418c-846d-eaeb02900fd6.jpg\"}, \"Front\": {\"Enabled\": true}, \"Speed\": 0.6, \"UseShader\": true, \"Difficulty\": 1, \"PrimaryColorHex\": \"#FFCC00\", \"ParticleColorHex\": \"#008000\", \"SecondaryColorHex\": \"#E6B800\"}}, \"game_config\": {\"difficulty\": \"Medium\", \"initial_lives\": 3, \"score_per_hit\": 50, \"game_duration_sec\": 90}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 2, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 2 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 2 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'aguardiente-amarillo'
);

-- campaign para game_id=4 (brand_id=exito)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"objects\": [{\"id\": \"manzana\", \"scale\": 2.5, \"score\": 50, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/af44ce5b-e4ba-4949-84c1-90d91ced0f92.png\"}, {\"id\": \"leche\", \"scale\": 2.5, \"score\": 50, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0859ddb5-0ebb-422e-b3b5-8f45f47fa378.png\", \"is_obstacle\": false}, {\"id\": \"pan\", \"scale\": 2.5, \"score\": 50, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1d4abffd-5eee-45ea-abfc-759917005a61.png\", \"is_obstacle\": false}, {\"id\": \"trash\", \"scale\": 2.5, \"score\": -20, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5f74f503-ae79-4087-be77-69dd75d4deaf.png\", \"is_obstacle\": true}], \"basket_speed\": 15, \"max_quantity\": 5, \"min_quantity\": 2, \"fall_speed_max\": 4, \"fall_speed_min\": 2, \"basket_sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/16b3cd44-c49d-4a3a-a2e3-4bc28f0d6813.png\", \"list_bg_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0d75dffe-8127-400f-8937-e9614d7e5418.png\", \"shopping_list_size\": 3}, \"meta\": {\"brand_id\": \"exito\", \"campaign_id\": \"\"}, \"audio\": {\"music_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"spawn_url\": \"https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg\", \"negative_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"positive_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\"}, \"texts\": {\"defeat_title\": \"HAS PERDIDO\", \"defeat_phrase\": \"Inténtalo de nuevo.\", \"victory_title\": \"¡HAS GANADO!\", \"victory_phrase\": \"¡Has recogido todos los objetos de la lista!\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/89795915-8bef-466f-b782-2f3dac136bc4.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/3b0a529e-d8a6-4089-b8d4-fd1b0ea71c48.webp\"}, \"shader_background_config\": {\"Back\": {\"Alpha\": 1, \"Speed\": 0, \"Tiling\": {\"x\": 1, \"y\": 1}, \"ColorHex\": \"#ffffff\", \"Rotation\": 0, \"Direction\": {\"x\": 0, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7a69d38c-6c11-4856-90f9-a215a557cfcd.webp\"}, \"Front\": {\"Alpha\": 0, \"Speed\": 0.2, \"Tiling\": {\"x\": 1, \"y\": 1}, \"Enabled\": false, \"ColorHex\": \"#fee502\", \"Rotation\": 0, \"Direction\": {\"x\": 1, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/eea60b88-3f7e-4453-bf76-0e37b64e53e2.png\"}}}, \"game_config\": {\"lives\": 3, \"duration\": 60, \"spawn_rate\": 1.2}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 4, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 4 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 4 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'exito'
);

-- campaign para game_id=5 (brand_id=drogueria-la-rebaja)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"words\": [{\"hint\": \"El servicio que te lleva las medicinas hasta la puerta de tu casa.\", \"word\": \"DOMICILIOS\", \"score\": 100}, {\"hint\": \"El estado de equilibrio y salud que siempre queremos proteger.\", \"word\": \"BIENESTAR\", \"score\": 100}, {\"hint\": \"El nombre de tu droguería de confianza en toda Colombia.\", \"word\": \"LA REBAJA\", \"score\": 200}], \"font_color_hex\": \"#FFFFFF\", \"power_ups_config\": [{\"cost\": 50, \"type\": \"RevealLetter\", \"icon_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1ab95c11-8a49-45cc-a2a5-7e0e8883eeaf.png\", \"color_hex\": \"#ffffffff\", \"display_name\": \"Hint\"}, {\"cost\": 30, \"type\": \"ZapOptions\", \"icon_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d3e4c776-8b4e-4e7c-94ff-8b611e762921.png\", \"color_hex\": \"#ffffffff\", \"display_name\": \"Zap\"}, {\"cost\": 100, \"type\": \"ExtraLife\", \"icon_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/18437170-ad14-461f-872f-e12f83d1d5a1.png\", \"color_hex\": \"#ffffffff\", \"display_name\": \"Life\"}], \"hangman_progress_urls\": [\"https://cdn.verygana.com/public/campaigns/designer-2/image/4688e57a-2503-460a-9120-bf318b282bf4.png\"]}, \"meta\": {\"brand_id\": \"drogueria-la-rebaja\", \"campaign_id\": \"\"}, \"audio\": {\"click_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/405564a8-bda7-4ebc-8ecd-c962c7e39918.mp3\", \"music_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"zap_sound_url\": \"https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg\", \"life_sound_url\": \"https://rtainor.com/verygana/musicmemory/windows_xp.mp3\", \"defeat_sound_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"reveal_sound_url\": \"https://cdn.aframe.io/360-image-gallery-boilerplate/audio/click.ogg\", \"victory_sound_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\"}, \"texts\": {\"defeat_title\": \"GAME OVER\", \"defeat_phrase\": \"Try Again\", \"victory_title\": \"VICTORY!\", \"victory_phrase\": \"Congratulations! You Won!\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/555162b8-2738-45e3-8c13-290819963861.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/865efce3-3728-4b34-b35c-7b250d7285f3.png\", \"keyboard_sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ebd5ff89-4312-4c99-9e13-1c21ecc2fcb2.png\", \"main_image_offset_y\": 0, \"logo_watermark_offset_y\": 0}, \"shader_background_config\": {\"Back\": {\"Alpha\": 1, \"Speed\": 0.2, \"Tiling\": {\"x\": 1, \"y\": 1}, \"ColorHex\": \"#ffffff\", \"Rotation\": 0, \"Direction\": {\"x\": 1, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e964c261-61a3-49c6-9a9d-a567fc030e58.png\"}, \"Front\": {\"Alpha\": 0, \"Speed\": 0.5, \"Tiling\": {\"x\": 1, \"y\": 1}, \"ColorHex\": \"#FFFFFF00\", \"Rotation\": 0, \"Direction\": {\"x\": 1, \"y\": 0}, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1e26c8df-884e-4657-9d25-e34b59147056.png\"}}}, \"game_config\": {\"difficulty\": \"normal\", \"time_limit\": 90, \"max_attempts\": 6}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 5, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 5 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 5 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'drogueria-la-rebaja'
);

-- campaign para game_id=6 (brand_id=ara)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"tiles\": [{\"id\": 1, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/aa2b2b8e-0893-43b6-aef5-ff3a228bebc6.png\"}, {\"id\": 2, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/25d0fc8a-632d-4b2a-9f75-c3bd07592f10.png\"}, {\"id\": 3, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/3adea538-417d-421e-ac86-3ad3bc90069b.png\"}, {\"id\": 4, \"score\": 10, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cbbcbb74-c049-4a43-9041-1cf4f7797507.png\"}, {\"id\": 5, \"score\": 15, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/56f2a4de-b423-4c1d-a0b0-d5c10eace4c3.png\"}, {\"id\": 6, \"score\": 15, \"sprite_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cf3c0baf-08be-472a-b2f0-6373cda0d32c.png\"}]}, \"meta\": {\"brand_id\": \"ara\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/55b471fb-4d35-4a8b-9a25-e974ee74b4f7.mp3\", \"swap_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8f75f8c2-d98c-4bec-b488-6fc9ae10ef2d.mp3\", \"match_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/53e57d6d-d388-4757-a5eb-d86db41d91df.mp3\", \"music_url\": \"https://rtainor.com/verygana/musicmemory/nokia.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/9a703273-d347-4373-9dfe-7e47f7fa4407.mp3\"}, \"texts\": {\"defeat_title\": \"¡Faltó ahorro!\", \"defeat_phrase\": \"Se te escaparon algunas ofertas. ¡Vuelve a jugar y llena tu carrito con los mejores precios de Ara!\", \"victory_title\": \"¡Alegría al mejor precio!\", \"victory_phrase\": \"¡Eres el rey del ahorro! Combinaste todas las ofertas y ganaste. Sigue disfrutando de la alegría de comprar en Ara.\"}, \"rewards\": {\"coins_per_action\": 10, \"combo_multiplier\": 0.1, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0d521b9f-5d5f-49cf-9aaa-2eee4d3ca366.png\", \"background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ff7e0046-ae3f-41be-9c0d-b4c82b1ef931.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/9c4868a7-e4aa-4821-bd9c-01c25b7255dd.png\", \"main_logo_offset_y\": 0, \"grid_background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c99f5c0d-9e0d-4e56-bdaa-77dd06711ab1.png\", \"background_color_hex\": \"#FFD100\", \"logo_watermark_offset_y\": 0, \"grid_background_color_hex\": \"#E30613\"}, \"background_config\": {\"Back\": {\"Speed\": 0.05, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0e60381e-a81f-4792-9b6f-c3d0bc51c8c3.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.2, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ba5fd231-9615-4b92-8804-35d96c6c8c03.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"difficulty\": \"normal\", \"time_limit\": 60, \"max_attempts\": 3, \"target_tiles\": 10, \"total_levels\": 5}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 6, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 6 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 6 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'ara'
);

-- campaign para game_id=7 (brand_id=d1)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"card_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d2db689a-f94a-4285-9f55-d19273f5937e.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fc93af66-e509-4b1c-906b-6dbe94ed4986.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e2dad50b-ad34-40c2-998e-1b09c94ba759.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/270c17b4-82e9-4cfe-8ace-54e37b7b948d.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/07be91c2-39ae-4fbb-b244-66af77db0457.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7e98dcf7-b9dd-4565-898a-daafefc3dd9d.png\"}]}, \"meta\": {\"brand_id\": \"d1\", \"campaign_id\": \"\"}, \"audio\": {\"flip_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/25934438-68d9-4cec-b9bc-73069ec76f73.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/f6185748-0c53-4c78-a8d2-444988a776d5.mp3\", \"error_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8076a72b-b625-48b7-ab3d-19ab050f7d9a.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/d9b61641-7743-4646-93e6-a4d01880a5a9.mp3\", \"powerup_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/772a2987-2991-4c1f-ab73-3d01f2a152b5.mp3\", \"success_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/96d031dc-ff7c-43d5-a758-a2fded25ce7a.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5566a321-b3b8-41a7-b3a1-c7b9f3a52e90.mp3\"}, \"texts\": {\"label_time\": \"Tiempo\", \"label_level\": \"Nivel\", \"label_score\": \"Llaves\", \"defeat_title\": \"¡Faltaron productos!\", \"defeat_phrase\": \"No lograste completar tu carrito a tiempo. ¡Concéntrate y vuelve a intentarlo!\", \"victory_title\": \"¡Excelente mercado!\", \"label_attempts\": \"Intentos\", \"victory_phrase\": \"¡Encontraste todas las parejas! Llevas la mejor calidad a los mejores precios con D1.\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"card_back_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/8515c0e7-d564-484c-b46e-9f57b35a3e34.png\", \"icon_hint_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/61ca4a2f-2b4a-42f8-a768-d16ea26bd651.png\", \"icon_life_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/67bf1d0d-170f-4a0c-bbae-6b048b0e531b.png\", \"icon_time_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a05d76a1-dca7-4672-90b7-e45217b80c12.png\", \"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/493ff24e-fba1-41e3-bf4c-6766f9dfecaf.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/1f50e91f-7b0c-4dad-81c1-805ad4bb8b15.png\", \"main_logo_offset_y\": 0, \"logo_watermark_offset_y\": 0}, \"background_config\": {\"Back\": {\"Speed\": 0.05, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f926e40a-f5ff-4e90-95ac-66594ce8b1b8.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.2, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/baa4b645-e4dc-4b17-a218-176ac46d722e.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"levels\": [], \"max_lives\": 3, \"final_pairs\": 8, \"start_pairs\": 3, \"enable_lives\": true, \"game_duration\": 90, \"bonus_per_pair\": 5, \"enable_powerups\": true, \"power_up_chance\": 0.2, \"preview_duration\": 3, \"bonus_time_amount\": 10}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 7, (SELECT id FROM target_audiences WHERE min_age IS NULL AND max_age IS NULL AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 7 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 7 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'd1'
);

-- campaign para game_id=8 (brand_id=la-fogata)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"tiles\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f35ff6a9-2d10-44f6-85fe-d33aa7679034.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a8e51804-dedc-4bac-b1a9-2f35b1a0ad76.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a979ea3a-2726-4eda-b0d1-1b91380e2099.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/6f851967-0b24-4313-abd7-e4f24ed3febc.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f18157b9-3579-4799-9203-c239ebbd0868.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/487d90b2-f925-4c41-80fd-d7ae38827629.jpg\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/06b4f16c-27ed-4bdf-a676-a0b8f9c4bc5e.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/ac1b0b41-f404-4ded-9074-84cb59fde58a.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/bc717b61-c5c1-401c-838c-634757cbae22.png\"}]}, \"meta\": {\"brand_id\": \"la-fogata\", \"campaign_id\": \"\"}, \"audio\": {\"bomb_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/87635d2b-d0c7-407e-958a-c27185581f1c.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/6e810e09-3465-4436-8687-ab1ed3c91bdd.mp3\", \"click_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/927be6ea-ae7f-4b66-99d5-ccfff7c75b93.mp3\", \"error_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8e4a401e-95b4-47bb-993e-305df1fb35bd.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5facf21d-36f6-4820-8dd8-354c9e56eadc.mp3\", \"rocket_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/23a8d057-267c-4e70-812d-961c97746eec.mp3\", \"whoosh_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/27a0a4db-91da-48d2-a138-26270ec92fb5.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/22c50af6-a604-4046-846e-03390d68be5d.mp3\", \"win_game_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/6fb0c1e9-1894-4a6d-93ac-07cd264b1487.mp3\"}, \"texts\": {\"label_time\": \"Fuego:\", \"label_score\": \"Sabor:\", \"defeat_title\": \"¡Se quemó el asado!\", \"label_errors\": \"Quemados:\", \"defeat_phrase\": \"La carne se pasó de término. ¡Vuelve a prender el fuego!\", \"victory_title\": \"¡Asado Perfecto!\", \"victory_phrase\": \"¡Eres el maestro parrillero! Todo quedó en su punto exacto.\", \"label_difficulty\": \"Término:\"}, \"rewards\": {\"coins_per_action\": 20, \"coins_on_completion\": 200}, \"branding\": {\"colors\": {\"btn_bg_hex\": \"#D9534F\", \"cell_bg_hex\": \"#FFF8DC\", \"grid_bg_hex\": \"#8B4513\", \"selected_hex\": \"#F0AD4E\", \"text_fixed_hex\": \"#333333\", \"unselected_hex\": \"#FFF8DC\", \"text_normal_hex\": \"#D9534F\"}, \"images\": {\"bomb_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fd17bc82-d600-4e7b-aa0c-48384e5f106d.png\", \"vertical_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fbe5f038-5c2d-4b87-825e-28e0f745182f.png\", \"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/2b6ea6fc-bbb0-4311-8fc5-355b662c6e86.png\", \"background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0135572f-4d0d-44ef-a8f1-07be7726e352.png\", \"horizontal_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/22d893c8-2593-454e-8456-7d5f12a0901d.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/506c3df6-30e6-4615-9391-a671e93d2e41.png\", \"main_logo_offset_y\": 0, \"cell_background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/4b88ddc2-297d-4754-9252-d63b59d87b23.png\", \"background_color_hex\": \"#2C1E16\", \"button_background_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/02f4bc4d-4512-443a-b2bf-799131dbc1db.png\", \"logo_watermark_offset_y\": 0}, \"background_config\": {\"Back\": {\"Speed\": 0.05, \"Enabled\": true, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d72973af-eba4-45dc-af51-78568b932eb4.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.2, \"Enabled\": false, \"ColorHex\": \"#FFFFFF\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/81416559-2e80-4598-b447-506c1b56763c.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"levels\": [{\"id\": 2, \"max_errors\": 3, \"time_limit\": 300, \"empty_cells\": 12, \"use_countdown\": true, \"enable_powerups\": true}], \"difficulty\": \"normal\", \"max_errors\": 3, \"time_limit\": 300, \"empty_cells\": 12, \"use_countdown\": true, \"enable_powerups\": true, \"warning_threshold\": 0.15}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 8, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 8 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 8 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'la-fogata'
);

-- campaign para game_id=9 (brand_id=don-pollo)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"meta\": {\"brand_id\": \"don-pollo\", \"campaign_id\": \"\"}, \"audio\": {\"coin_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/17d4df8a-3cc2-4384-aa6d-26b0cd8f629b.mp3\", \"jump_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/2e09743d-9215-4f30-a035-671bf91fa0e3.mp3\", \"land_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/fc9618c1-20a1-4fdb-9693-ec8a2bdfd5aa.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/3aa8fcfa-577e-4ad7-8a3b-aeba33a2d4a0.mp3\", \"death_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/9b2789c6-fcf5-4109-a512-a8a14d1ae80e.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/af9bf4fd-6a0e-4ef9-89c7-4a49cec1bdd8.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8570d3a6-d676-400c-88eb-24e0a108c61a.mp3\"}, \"texts\": {\"label_time\": \"TIEMPO\", \"label_score\": \"LLAVES\", \"defeat_title\": \"¡Tropiezo!\", \"label_record\": \"RÉCORD\", \"defeat_phrase\": \"Te enredaste en el camino. ¡Vuelve a intentarlo y llega más lejos!\", \"victory_title\": \"¡Gran Carrera!\", \"victory_phrase\": \"¡Llegaste a la meta esquivando todos los obstáculos con éxito! Disfruta tu premio.\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b005a92c-fdae-495c-a222-61376fa368db.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/0075a4ac-2ed3-4e05-9d6d-55db889ecd7c.png\", \"main_logo_offset_y\": 50, \"logo_watermark_offset_y\": -50}, \"visuals\": {\"coin_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a90a5c75-a1e8-476b-90ad-76cd9bb77421.png\", \"coin_scale\": 1.2, \"ground_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/3946274b-efdd-418b-ab7e-b4555cad18bb.jpg\", \"player_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/707d17f1-d231-4929-85f3-d37d879897f6.png\", \"dw_bg_color\": \"#220000\", \"dw_scroll_x\": 0.5, \"dw_scroll_y\": 0.5, \"air_trap_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e8a09db2-e69e-4523-aabc-4a4c6247336f.png\", \"bg_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e9fa4b51-06b2-4e01-81be-0aa84a2995ca.jpg\", \"ground_color\": \"#ffffff\", \"dw_desphase_x\": 0.1, \"dw_desphase_y\": 0.1, \"air_trap_scale\": 1, \"bg_solid_color\": \"#1a0b2e\", \"death_wall_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d214a8ae-34d0-4e7d-ac4a-123372fedeeb.png\", \"ground_trap_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/554d9bc7-e271-41f6-9d4b-cce2c4cc92ec.png\", \"bg_texture_scale\": 1, \"dw_primary_color\": \"#ff0000\", \"ground_trap_scale\": 0.9, \"dw_secondary_color\": \"#550000\", \"ground_texture_scale\": 1}, \"background_config\": {\"Back\": {\"Speed\": 0, \"Enabled\": true, \"ColorHex\": \"#0A0A1E\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c970230b-3d2e-4fcc-890d-6666d48ef473.png\", \"LayoutMode\": \"Stretched\", \"AspectRatio\": 1.77}, \"Front\": {\"Speed\": 0.1, \"Enabled\": true, \"ColorHex\": \"#FFFFFF80\", \"Rotation\": 0, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7f731003-0794-47c3-8ec2-65a461a8b4bb.png\", \"LayoutMode\": \"TiledSquare\", \"AspectRatio\": 1}}}, \"game_config\": {\"max_lives\": 3, \"jump_force\": 12, \"acceleration\": 0.05, \"scroll_speed\": 4, \"game_duration\": 60, \"gravity_scale\": 3, \"use_countdown\": true, \"max_scroll_speed\": 10}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 9, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 9 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 9 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'don-pollo'
);

-- campaign para game_id=10 (brand_id=poker)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"hitColorHex\": \"#FFFFFF\", \"hitSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/88573fb6-fed4-4fc3-8abc-edefa2c935f1.jpg\", \"holeColorHex\": \"#FFFFFF\", \"moleColorHex\": \"#FFFFFF\", \"errorColorHex\": \"#FFFFFF\", \"holeSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/59b9475d-57f1-4210-afb5-742f247c2edb.png\", \"moleSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c32c0c25-4c99-42bd-a38b-7c773a822d66.jpg\", \"errorSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d04f436f-88d2-431f-8334-b109f1d6fff1.png\", \"showPreviewButtons\": true, \"previewButtonColorHex\": \"#FFFFFF\", \"previewButtonSpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/43535fcb-a77c-4062-b5ff-7cd18b57957c.png\"}, \"meta\": {\"brand_id\": \"poker\", \"campaign_id\": \"\"}, \"audio\": {\"win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/bf580665-3d9f-4213-95a7-2c2d52d97d51.mp3\", \"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5068bca3-e2cb-4701-aaca-9672f0012d9e.mp3\", \"music_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/2d7dd1cd-67e3-4304-9fd8-f3b18cd0a218.mp3\", \"hit_sfx_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/b5b5af15-a432-4d16-89b2-71de80328aa1.mp3\", \"miss_sfx_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/7ab90f37-5227-480d-8d91-57f052c3ef94.mp3\", \"main_audio_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5acb4d4e-e836-4631-a90f-f852bf2d09e5.mp3\"}, \"texts\": {\"defeat_title\": \"¡Se te enfriaron!\", \"defeat_phrase\": \"Se te escaparon demasiadas botellas de las manos. ¡Vuelve a intentarlo y demuestra tus reflejos!\", \"victory_title\": \"¡Amigos y Polas!\", \"victory_phrase\": \"¡Atrapaste todas las botellas a tiempo! El parche está asegurado y la diversión no para.\"}, \"rewards\": {\"coins_per_action\": 10, \"coins_on_completion\": 100}, \"branding\": {\"images\": {\"main_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/53117176-5df7-40f3-b5af-6a66571badc6.png\", \"logo_watermark_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/e3167dd2-e5f5-4455-b9cd-4ccf4eb5042b.png\"}, \"shader_background_config\": {\"Back\": {\"Alpha\": 1, \"ColorHex\": \"#FFFFFF\", \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/304d5c7c-6ccf-436c-be73-cb4ef67d185b.png\"}, \"Front\": {\"Enabled\": false, \"SpriteUrl\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/67773ad2-913f-4c28-8565-90a4c2fc46e6.png\"}}}, \"game_config\": {\"duration\": 60, \"gridCols\": 3, \"gridRows\": 3, \"maxLives\": 3, \"moleLifetime\": 2, \"pointsPerHit\": 10, \"spawnInterval\": 0.8, \"audioDivisions\": 3}, \"personalization\": {\"coin_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\", \"coin_count_url\": \"https://cdn.verygana.com/public/logos/moneda-llave.png\"}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 10, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 10 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 10 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'poker'
);

-- campaign para game_id=11 (brand_id=unicentro)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"character_color\": \"#ffffff\", \"background_phrases\": [\"El poder de hacer lo posible\", \"Alcanza tus metas financieras\", \"Ahorrar es tu mejor jugada\", \"Tu plata segura desde la App\"], \"character_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5e815ad0-1f9e-4ed5-9d69-dc258ef7a686.jpg\", \"key_spawn_probability\": 0.3}, \"meta\": {\"brand_id\": \"unicentro\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/828022f8-6177-4fef-9dce-596b4311f5bc.mp3\", \"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/b0aa57e0-3e39-4d6c-9858-9ee3041cbd93.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Cuidado con los gastos hormiga! Cuida tu plata y vuelve a intentarlo.\", \"Un pequeño tropiezo financiero. ¡Levántate y sigue corriendo hacia tus metas!\", \"¡Cuidado con los gastos hormiga! Cuida tu plata y vuelve a intentarlo.\", \"Tus sueños te siguen esperando. ¡Revisa tu estrategia y juega de nuevo!\"]}, \"rewards\": {\"keys_per_action\": 10}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7f10ee33-c853-40d1-aa3b-8ae945f93cc4.jpg\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, 3,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 11, (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 11 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 11 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'unicentro'
);

-- campaign para game_id=12 (brand_id=netflix)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"character_id\": -1, \"wood_sign_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/36e8a1dd-f651-422c-aac1-d22c76fa80e4.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b2717f78-52cb-4205-acce-0c769b949e9f.png\"}], \"wood_sign_phrases\": [\"¿Sigues ahí?\", \"Solo un capítulo más\", \"Spoiler Alert!\", \"Alerta de Maratón\"], \"key_spawn_probability\": 0.3}, \"meta\": {\"brand_id\": \"netflix\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5e109b52-4abe-4a48-900a-71ee9b470de5.mp3\", \"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/4e502807-4d92-4ee0-bbec-c9bfcac1af8c.mp3\"}, \"texts\": {\"game_over_messages\": [\"¿Sigues ahí? ¡Dale a reiniciar para continuar tu maratón!\", \"Fin de temporada. ¡Corre por una nueva oportunidad!\", \"Se cortó el internet. ¡Vuelve a intentarlo!\"]}, \"rewards\": {\"keys_per_action\": 5}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5e008ffc-f341-491a-bff3-a02a0b10292e.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, 3,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 12, (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 12 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 12 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'netflix'
);

-- campaign para game_id=13 (brand_id=colgate)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"card_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f335e101-8614-4445-b317-f898f0d508b0.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cbd935bb-7a80-4a7c-9705-59b8558c1555.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/45e1d103-95a9-4516-8e91-0deae760724a.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/4ce7c4ca-bbc3-47e8-a9b4-b68b9e37aa14.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/f9e0f345-174c-4290-8330-22c446680d34.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/6da6e5ae-22f8-43de-a5ec-2e61e18475c9.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d599ce76-5a7d-4a30-8e4b-d9c99699b8ae.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b23b35de-af0f-4a91-b78b-75deba3bb578.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/8f1791c5-5a5d-4f61-ae75-d1baf1b05e1e.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/87a032c6-1be6-4e39-b806-5efe57959bab.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cd37a1d9-7c15-4bdc-9249-cf85c7dfdf44.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/c5d3c5ef-9d14-4ef9-8811-7f4e5962adcf.png\"}]}, \"meta\": {\"brand_id\": \"colgate\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/76f58f83-ed0a-4031-980e-653858256b6e.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/99cbdf02-1f2e-4629-9a3d-343acb4eac8f.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Sonrisa brillante! Encontraste todas las parejas.\", \"¡Memoria 100% libre de caries! Eres un experto en frescura.\", \"¡Misión cumplida! Tu memoria es tan fuerte como tus dientes.\", \"¡Protección total! Ganaste el reto Colgate.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 100}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/47fb34c8-6a5c-488d-80a2-fd7ff6adbc5f.jpg\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/55738786-cf06-4e74-8963-028a7f5ff239.jpg\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, 3,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 13, (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 13 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 13 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'colgate'
);

-- campaign para game_id=14 (brand_id=claro)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"rock_logos\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/789959c4-0cff-4f81-97db-735a23746723.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/2a514278-3891-4066-a00a-8f4d76308af1.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/4876bc0b-d035-41a3-b2a0-64aadbac4b4b.png\"}], \"plane_words\": [\"Velocidad Total\", \"Máxima Señal\", \"Conexión Pro\", \"Todo Ilimitado\", \"Red Más Rápida\"], \"character_id\": -1, \"character_colors\": [\"#da291c\", \"#FFFFFF\", \"#000000\"], \"key_spawn_probability\": 0.35}, \"meta\": {\"brand_id\": \"claro\", \"campaign_id\": \"\"}, \"audio\": {\"lose_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/8a04123d-bd75-43ed-8550-b49c7a1aaf44.mp3\", \"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/7bb1b6b1-27ce-49f2-9953-0becac9a3426.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Te quedaste sin datos para esquivar! Reinicia tu partida.\", \"¡Se cayó la conexión en tu carrera! Vuelve a intentarlo.\", \"Encontramos un obstáculo en la red. ¡Dale a jugar otra vez!\"]}, \"rewards\": {\"keys_per_action\": 10}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d2be40e9-9215-472c-9f9b-1d1e069e565b.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, 3,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 14, (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 14 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 14 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'claro'
);

-- campaign para game_id=15 (brand_id=sura)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"entries\": [{\"clue\": \"Bienestar físico y mental que cuidamos todos los días.\", \"word\": \"SALUD\"}, {\"clue\": \"Respaldo y protección financiera ante cualquier imprevisto.\", \"word\": \"SEGURO\"}, {\"clue\": \"Estado de equilibrio y calidad de vida para las personas.\", \"word\": \"BIENESTAR\"}, {\"clue\": \"Lo construimos hoy con planeación y tranquilidad.\", \"word\": \"FUTURO\"}, {\"clue\": \"Práctica financiera para alcanzar tus metas a largo plazo.\", \"word\": \"AHORRO\"}, {\"clue\": \"Lo más valioso que acompañamos a proteger en cada etapa.\", \"word\": \"VIDA\"}, {\"clue\": \"Cobertura integral para ti y tu familia ante riesgos.\", \"word\": \"PROTECCION\"}, {\"clue\": \"El lazo de seguridad que generamos contigo cada día.\", \"word\": \"CONFIANZA\"}, {\"clue\": \"Análisis del entorno para anticiparnos al mañana.\", \"word\": \"TENDENCIAS\"}, {\"clue\": \"Cuidado anticipado para cuidar tu salud y bienestar.\", \"word\": \"PREVENCION\"}]}, \"meta\": {\"brand_id\": \"sura\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/70d76262-32f7-4ace-88fc-5598231ac13c.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/f84e8256-c8d6-433f-9e53-f59aee1bbfe7.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Excelente trabajo! Tu mente y tu bienestar van de la mano.\", \"¡Completaste el reto! Así de claro es construir un futuro seguro con Sura.\", \"¡Lo lograste! Cuidar de ti también es un juego de concentración.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/b3ce4c00-f443-4a0f-b4c3-1232a84c2a52.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7d2e0964-0571-44bf-aa52-4f47020eff8c.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, 3,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 15, (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 15 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 15 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'sura'
);

-- campaign para game_id=16 (brand_id=postobon)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"container_colors\": [\"#ee4c98\", \"#ee4c98\", \"#0f62a0\"], \"container_images\": [{\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a77423df-e9d1-415f-8438-cbb03eb5c58a.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/65d9fc21-cf95-45af-a8d0-88dd29cb2b19.png\"}, {\"url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/cec4068f-92ab-41ee-9d1a-ab655ba5dae5.png\"}], \"containers_per_key\": 4}, \"meta\": {\"brand_id\": \"postobon\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5482492c-c7c0-4993-b7cd-4cebe2f46454.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/05613c1f-e300-4c5d-9692-62f4f9b42ab7.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Se nos acabó el gas! Tómate un respiro y vuelve a jugar.\", \"¡Te faltó un sorbo para ganar! Destapa una nueva oportunidad.\", \"¡La sed de victoria sigue intacta! Reinicia y refresca tu puntaje.\"]}, \"rewards\": {\"keys_per_action\": 15}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/a20e7752-947c-499f-86cb-9edfd4b527d7.webp\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, 3,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 16, (SELECT id FROM target_audiences WHERE min_age = 18 AND max_age = 100 AND target_gender = 'ALL' LIMIT 1)
FROM game_config_definitions gcd
WHERE gcd.game_id = 16 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 16 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'postobon'
);

-- campaign para game_id=17 (brand_id=bancolombia)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"piece_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/55da063a-de9f-4827-9416-e5aec5eb63ce.png\", \"round_win_phrases\": [\"¡Llenando las bolsas!\", \"¡Encontraste la tienda perfecta!\", \"¡Una compra exitosa!\", \"¡Llevas el mejor estilo!\"]}, \"meta\": {\"brand_id\": \"bancolombia\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/d47574fe-948e-47dc-8180-1e341b64dd08.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/bf14303b-9cb6-477b-ac67-a6bb835e7e01.mp3\", \"game_over_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/e451e25f-4c0a-48c7-b2a9-6220ffdfc2f3.mp3\"}, \"texts\": {\"game_over_messages\": [\"¡Uy! Te pesaron mucho las bolsas. ¡Toma un respiro y vuelve a jugar!\", \"Te distrajiste viendo las vitrinas. ¡Concéntrate y atrapa todas las piezas!\", \"¡No te vayas con las manos vacías! Reinicia la ronda y sigue comprando.\"]}, \"rewards\": {\"keys_per_action\": 20, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/fafa8507-e3a8-4c90-baa7-ebbe5a79ae15.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/431a80e4-6dcb-4abb-8b61-e0ce33fb4129.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 17, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 17 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 17 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'bancolombia'
);

-- campaign para game_id=18 (brand_id=hotel-las-bailarinas)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"puzzle_image_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/7e9a6c5e-3318-4e7c-8a81-6686e5a5df27.jpg\"}, \"meta\": {\"brand_id\": \"hotel-las-bailarinas\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/24fb448d-91d0-4cbb-9f86-f91b81632076.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/ff2ac9f4-597b-47d3-baee-f2c296375c5b.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Rompecabezas completado! Tu descanso ideal en la naturaleza te está esperando.\", \"¡Perfecto! Cada pieza encaja tan bien como tus vacaciones soñadas en Las Bailarinas.\", \"¡Lograste armar la diversión! Ahora relájate y prepárate para vivir la experiencia.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/5686d840-42fe-4666-bf8b-53937f41e59a.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/91ecd92e-b79b-4706-b7a9-c7f2188d6393.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 18, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 18 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 18 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'hotel-las-bailarinas'
);

-- campaign para game_id=19 (brand_id=parque-del-cafe)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"questions\": [{\"id\": 1, \"options\": [\"La caña de azúcar\", \"La cultura cafetera\", \"La historia precolombina\", \"La minería\"], \"question\": \"¿Cuál es la temática principal del Parque del Café?\", \"correct_answer_index\": 1}, {\"id\": 2, \"options\": [\"Salento\", \"Filandia\", \"Montenegro\", \"Quimbaya\"], \"question\": \"¿En qué municipio está ubicado el Parque del Café?\", \"correct_answer_index\": 2}, {\"id\": 3, \"options\": [\"Krater\", \"Cumbre\", \"La Broca\", \"Yippe\"], \"question\": \"¿Cómo se llama la icónica montaña rusa del parque que tiene una caída de 90 grados?\", \"correct_answer_index\": 0}, {\"id\": 4, \"options\": [\"El Tren del Café\", \"El Teleférico\", \"El Helicóptero\", \"La Tirolina\"], \"question\": \"¿Qué medio de transporte panorámico te permite cruzar el parque desde las alturas?\", \"correct_answer_index\": 1}, {\"id\": 5, \"options\": [\"El Secreto de la Montaña\", \"Magia Cafetera\", \"El Show del Café\", \"Danza del Recolector\"], \"question\": \"¿Cuál es el nombre del espectáculo principal que narra la historia del grano en Colombia?\", \"correct_answer_index\": 2}, {\"id\": 6, \"options\": [\"Los Carros Chocones\", \"La Montaña Rusa Yippe\", \"El Paseo a Caballo\", \"El Safari\"], \"question\": \"¿Qué atracción está inspirada en el tradicional Jeep Willys de la región?\", \"correct_answer_index\": 1}, {\"id\": 7, \"options\": [\"La Rueda de la Fortuna\", \"El Faro\", \"El Balcón del Quindío\", \"La Torre Mirador\"], \"question\": \"¿Desde qué estructura puedes tener la mejor vista panorámica de la región cafetera?\", \"correct_answer_index\": 3}, {\"id\": 8, \"options\": [\"La Federación Nacional de Cafeteros\", \"El Ministerio de Cultura\", \"La Gobernación local\", \"Una empresa privada de turismo\"], \"question\": \"¿Qué entidad fundó el Parque del Café en el año 1995?\", \"correct_answer_index\": 0}, {\"id\": 9, \"options\": [\"El Río Lento\", \"Los Rápidos\", \"La Cascada\", \"Botes Chocones\"], \"question\": \"¿Cómo se llama la atracción donde te subes a botes circulares para un recorrido acuático?\", \"correct_answer_index\": 1}, {\"id\": 10, \"options\": [\"El Tranvía\", \"La Carroza\", \"El Tren del Café\", \"El Funicular\"], \"question\": \"¿Qué medio de transporte clásico recorre la parte baja del parque bordeando el bosque?\", \"correct_answer_index\": 2}]}, \"meta\": {\"brand_id\": \"parque-del-cafe\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/5b2e512a-bd34-4c67-bab3-f10c1818abb0.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/246f0839-eca1-45c5-a7e3-f5f0b890360e.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Excelente! Conoces el Parque del Café como un verdadero experto de nuestra tierra.\", \"¡Puntaje perfecto! Estás listo para subirte al Yippe y celebrar tu victoria con toda la adrenalina.\", \"¡Qué gran conocimiento! Disfruta de la magia cafetera y sigue sumando llaves en tu cuenta.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/819140df-0980-4f4f-977b-e7649d7a30b5.webp\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/472d0836-df12-49af-83e2-8974a74f37b7.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 19, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 19 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 19 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'parque-del-cafe'
);

-- campaign para game_id=20 (brand_id=colanta)
INSERT INTO campaigns (
    average_reward_per_session_cents, budget_cents, completed_sessions, completion_reward_cents,
    config_data, created_at, end_date, max_reward_per_session_cents, max_session_per_user_per_day,
    score_reward_factor, sessions_played, spent_cents, start_date, status, total_play_time_seconds,
    unique_players_count, updated_at, version, commercial_id, config_definition_id, game_id, target_audience_id
)
SELECT
    gcd.average_reward_per_session_cents, 1000000, 0, gcd.completion_reward_cents,
    '{\"game\": {\"words\": [{\"word\": \"COLANTA\", \"color\": \"#008A4B\"}, {\"word\": \"LECHE\", \"color\": \"#005AA9\"}, {\"word\": \"QUESO\", \"color\": \"#FFC107\"}, {\"word\": \"CAMPO\", \"color\": \"#27AE60\"}, {\"word\": \"YOGURT\", \"color\": \"#E31837\"}, {\"word\": \"AVENA\", \"color\": \"#E67E22\"}, {\"word\": \"CREMA\", \"color\": \"#00A3E0\"}, {\"word\": \"SUERO\", \"color\": \"#8E44AD\"}, {\"word\": \"FRESCO\", \"color\": \"#00BFFF\"}, {\"word\": \"SABOR\", \"color\": \"#F39C12\"}]}, \"meta\": {\"brand_id\": \"colanta\", \"campaign_id\": \"\"}, \"audio\": {\"key_win_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/3ac3490f-8c20-4fe0-b51a-7c18b18079f4.mp3\", \"victory_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/audio/fd7e58a8-777b-4651-a44d-fd7f06f5006a.mp3\"}, \"texts\": {\"victory_messages\": [\"¡Sabe más! Encontraste todas las palabras y toda la frescura de Colanta.\", \"¡Excelente! Demostraste que tienes el campo y la nutrición en tu mente.\", \"¡Sopa de letras completada! Disfruta del verdadero sabor del campo.\"]}, \"rewards\": {\"keys_per_action\": 10, \"keys_on_completion\": 150}, \"branding\": {\"main_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/d1d91f06-3c5b-4efc-bb73-b144174ba958.png\", \"watermark_logo_url\": \"https://cdn.verygana.com/public/campaigns/designer-2/image/24ea7061-75dc-46b4-8891-eb75caeae033.png\"}, \"game_config\": {}}', NOW(), DATE_ADD(NOW(), INTERVAL 90 DAY), gcd.max_reward_per_session_cents, NULL,
    gcd.score_reward_factor, 0, 0, NOW(), 'ACTIVE', 0,
    0, NOW(), 0, (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com'), gcd.id, 20, NULL
FROM game_config_definitions gcd
WHERE gcd.game_id = 20 AND gcd.is_latest = 1
AND NOT EXISTS (
    SELECT 1 FROM campaigns c
    WHERE c.game_id = 20 AND c.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
      AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = 'colanta'
);

-- Enlaza cada branding_request con la campaña que generó, por meta.brand_id
-- (game_id + commercial_id ya no identifica una fila única: ver nota de dedupe
-- arriba).
UPDATE branding_requests br
JOIN campaigns c
  ON c.commercial_id = br.commercial_id
 AND c.game_id = br.game_id
 AND JSON_UNQUOTE(JSON_EXTRACT(c.config_data, '$.meta.brand_id')) = JSON_UNQUOTE(JSON_EXTRACT(br.game_config, '$.meta.brand_id'))
SET br.campaign_id = c.id
WHERE br.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
  AND br.campaign_id IS NULL;

-- Sesiones de juego previas (8 por campaña) para que el panel de métricas del
-- empresario tenga datos. consumer_id cicla entre los 10 consumidores fijos de
-- beta_users.sql (prefijo 0be7a000-0005).
INSERT INTO game_sessions (
    coins_earned, completed, device_platform, end_time, play_time_seconds,
    reward_granted, score, session_token, start_time, user_hash,
    campaign_id, consumer_id, game_id
)
SELECT
    40 + (seq.n * 5),
    seq.n % 4 != 0,
    ELT(1 + (seq.n % 3), 'MOBILE', 'PC', 'TABLET'),
    DATE_SUB(NOW(), INTERVAL (seq.n * 2) DAY) + INTERVAL (90 + seq.n * 7) SECOND,
    60 + seq.n * 7,
    seq.n % 4 != 0,
    200 + seq.n * 15,
    CONCAT('beta-branding-', brand.id, '-', seq.n),
    DATE_SUB(NOW(), INTERVAL (seq.n * 2) DAY),
    CONCAT('beta-hash-', brand.id, '-', seq.n),
    brand.id,
    cons.user_id,
    brand.game_id
FROM campaigns brand
JOIN (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
    UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
) seq
JOIN (
    SELECT 1 AS n, '0be7a000-0005-0000-0000-000000000001' AS public_id
    UNION ALL SELECT 2, '0be7a000-0005-0000-0000-000000000002'
    UNION ALL SELECT 3, '0be7a000-0005-0000-0000-000000000003'
    UNION ALL SELECT 4, '0be7a000-0005-0000-0000-000000000004'
    UNION ALL SELECT 5, '0be7a000-0005-0000-0000-000000000005'
    UNION ALL SELECT 6, '0be7a000-0005-0000-0000-000000000006'
    UNION ALL SELECT 7, '0be7a000-0005-0000-0000-000000000007'
    UNION ALL SELECT 8, '0be7a000-0005-0000-0000-000000000008'
    UNION ALL SELECT 9, '0be7a000-0005-0000-0000-000000000009'
    UNION ALL SELECT 10, '0be7a000-0005-0000-0000-00000000000a'
) consumer_seed ON consumer_seed.n = 1 + ((seq.n + brand.game_id) % 10)
JOIN users cu ON cu.public_id = UUID_TO_BIN(consumer_seed.public_id)
JOIN consumer_details cons ON cons.user_id = cu.id
WHERE brand.commercial_id = (SELECT cd.user_id FROM commercial_details cd JOIN users u ON u.id = cd.user_id WHERE u.email = 'comercial.premium@verygana.com')
  AND JSON_UNQUOTE(JSON_EXTRACT(brand.config_data, '$.meta.brand_id')) IN (
      'aguardiente-amarillo','exito','drogueria-la-rebaja','ara','d1','la-fogata','don-pollo','poker',
      'unicentro','netflix','colgate','claro','sura','postobon','bancolombia','hotel-las-bailarinas',
      'parque-del-cafe','colanta'
  )
  AND NOT EXISTS (
      SELECT 1 FROM game_sessions gs
      WHERE gs.session_token = CONCAT('beta-branding-', brand.id, '-', seq.n)
  );

-- Contadores agregados de la campaña (sessions_played, completed_sessions,
-- unique_players_count, total_play_time_seconds, spent_cents), coherentes con
-- las sesiones insertadas arriba. Se usan en los totales 'lifetime' del reporte
-- ejecutivo (CommercialReportServiceImpl); el rango de fechas del panel se
-- calcula en vivo sobre game_sessions, no sobre estos contadores.
UPDATE campaigns c
JOIN (
    SELECT
        campaign_id,
        COUNT(*) AS sessions_played,
        SUM(completed = 1) AS completed_sessions,
        COUNT(DISTINCT consumer_id) AS unique_players_count,
        SUM(play_time_seconds) AS total_play_time_seconds,
        SUM(coins_earned) AS coins_total
    FROM game_sessions
    WHERE session_token LIKE 'beta-branding-%'
    GROUP BY campaign_id
) agg ON agg.campaign_id = c.id
SET
    c.sessions_played = agg.sessions_played,
    c.completed_sessions = agg.completed_sessions,
    c.unique_players_count = agg.unique_players_count,
    c.total_play_time_seconds = agg.total_play_time_seconds,
    c.spent_cents = agg.coins_total;
