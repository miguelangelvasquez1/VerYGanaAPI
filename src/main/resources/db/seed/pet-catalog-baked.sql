-- ============================================================================
-- Catálogo de mascotas: ítems horneados en el build de Unity
-- ============================================================================
-- Por qué existe este archivo:
--   spendKeysForPetGame resuelve el precio contra pet_catalog_items. Si el ítem
--   comprado no está en la tabla, se cobra EL MONTO QUE MANDA EL NAVEGADOR.
--   Sembrar estas filas mueve el precio al servidor sin tocar el build.
--
-- Cómo identifica el juego cada cosa:
--   • Comida  -> external_id numérico  (findByExternalId)
--   • Ropa    -> nombre interno        (findByNameIgnoreCase, external_id NULL)
--
-- active = false a propósito: estos ítems ya vienen dentro del build, así que no
-- deben volver a listarse en POST /pet/catalog. La resolución de precio NO filtra
-- por active, de modo que el cobro correcto sigue funcionando.
--
-- Precios en llaves (1 llave = financial.key-value-cents). Verificados contra la
-- BD el 2026-08-19. Idempotente: se puede correr varias veces.
-- ============================================================================

-- ── Comida y bebida (por external_id) ───────────────────────────────────────
INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (0, 'Apple', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 1, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (1, 'Broccoli', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 2, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (2, 'Burger', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 5, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (3, 'Double Burger', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 15, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (4, 'Cabbage', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 10, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (5, 'EnergyDrink', 'Horneado en el build de Unity: el juego lo identifica por external_id', NULL, NULL, NULL, 100, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (6, 'Ice-Cream', 'Horneado en el build de Unity: el juego lo identifica por external_id', NULL, NULL, NULL, 11, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (7, 'Pear', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 9, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (8, 'Pizza', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 20, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (9, 'Popsicle', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 5, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (10, 'Sandwich', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 20, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (11, 'Strawberry', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 35, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (12, 'Tomato', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 65, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

INSERT INTO pet_catalog_items (external_id, name, description, is_medicine, is_drink, cures_all_parts, price, active)
VALUES (14, 'Water', 'Horneado en el build de Unity: el juego lo identifica por external_id', 0, 0, 0, 11, false)
ON DUPLICATE KEY UPDATE name = VALUES(name), price = VALUES(price);

-- ── Ropa, accesorios y acciones del juego (por nombre) ──────────────────────
INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'astronaut', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('astronaut'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'cap', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('cap'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'chef', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('chef'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'cowboy', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('cowboy'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'crown', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('crown'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'devilmask', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('devilmask'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'gladiator', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('gladiator'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'goku', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('goku'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'horns', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('horns'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'monoculo', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('monoculo'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'nickname_change', 'Accion del juego: cambiar el nombre de la mascota', 150, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('nickname_change'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'ninja', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('ninja'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'one_piece_hat', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('one_piece_hat'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'party', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('party'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'pirate', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('pirate'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'sunglasses', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('sunglasses'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'tomahawk', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('tomahawk'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'viking', 'Ropa (horneada en el build)', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('viking'));


-- ── Colores del selector de mascota (por nombre) ─────────────────────────────
-- PetColorPicker manda itemName = "color_" + ColorUtility.ToHtmlStringRGBA(color),
-- o sea el hexadecimal RRGGBBAA en MAYÚSCULAS y con el alfa incluido. Se resuelven
-- por nombre igual que la ropa, así que van con external_id NULL.
--
-- El orden es el del selector en pantalla (café → gris → violeta → azul → cian →
-- verde → naranja → amarillo → crema → rosa → rojo), no alfabético: así se ubica
-- un color en esta lista mirando el juego. Los nombres salieron uno a uno del WARN
-- de KeyWalletServiceImpl al comprarlos todos, NO de leer la paleta del build: el
-- hex que emite Unity redondea y no se puede deducir del color del editor.
--
-- Los tres colores base (blanco, negro y gris medio) no aparecen aquí porque
-- IsColorUnlocked los da por desbloqueados y nunca llegan a cobrarse.

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_5C4033FF', 'Color de mascota (cafe oscuro) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_5C4033FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_70808FFF', 'Color de mascota (gris azulado) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_70808FFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_8C592BFF', 'Color de mascota (cafe) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_8C592BFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_999999FF', 'Color de mascota (gris) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_999999FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_D9D9D9FF', 'Color de mascota (gris claro) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_D9D9D9FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_ED82EDFF', 'Color de mascota (violeta) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_ED82EDFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_C7A3F5FF', 'Color de mascota (lila) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_C7A3F5FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_9470DBFF', 'Color de mascota (purpura medio) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_9470DBFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_4A0082FF', 'Color de mascota (indigo) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_4A0082FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_1F338CFF', 'Color de mascota (azul marino) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_1F338CFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_4287F5FF', 'Color de mascota (azul) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_4287F5FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_87CFFAFF', 'Color de mascota (azul cielo) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_87CFFAFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_00FFFFFF', 'Color de mascota (cian) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_00FFFFFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_00CFD1FF', 'Color de mascota (turquesa oscuro) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_00CFD1FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_40E0D1FF', 'Color de mascota (turquesa) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_40E0D1FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_99FA99FF', 'Color de mascota (verde claro) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_99FA99FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_546B2EFF', 'Color de mascota (verde oliva) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_546B2EFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_2E8C57FF', 'Color de mascota (verde mar) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_2E8C57FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_80CC1AFF', 'Color de mascota (verde lima) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_80CC1AFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FF8C1AFF', 'Color de mascota (naranja) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FF8C1AFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_F5C24CFF', 'Color de mascota (mostaza) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_F5C24CFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FFBF00FF', 'Color de mascota (ambar) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FFBF00FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FFE64CFF', 'Color de mascota (amarillo) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FFE64CFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FFD999FF', 'Color de mascota (durazno) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FFD999FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_D1B58CFF', 'Color de mascota (beige) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_D1B58CFF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_F5EBD1FF', 'Color de mascota (crema) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_F5EBD1FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_D970D6FF', 'Color de mascota (orquidea) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_D970D6FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FF1494FF', 'Color de mascota (rosa fuerte) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FF1494FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FA8073FF', 'Color de mascota (salmon) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FA8073FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_FF6961FF', 'Color de mascota (coral) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_FF6961FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_8C0D26FF', 'Color de mascota (vino) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_8C0D26FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_E63333FF', 'Color de mascota (rojo) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_E63333FF'));

INSERT INTO pet_catalog_items (external_id, name, description, price, active)
SELECT NULL, 'color_CC1A4CFF', 'Color de mascota (carmesi) - horneado en el build', 50, false
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM pet_catalog_items p WHERE LOWER(p.name) = LOWER('color_CC1A4CFF'));
