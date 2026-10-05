-- ============================================================================
-- Ítems de un comercial en el mercado de mascotas (beta)
-- ============================================================================
-- Deja lo mismo que el flujo real cuando el diseñador publica una solicitud
-- (CatalogIntegrationRequestServiceImpl#publishCatalogItem): un ítem activo en
-- pet_catalog_items y su solicitud en COMPLETED apuntando a él.
--
-- El comercial no se crea acá: ya existe en beta y se busca por su public_id. Si
-- no está (en dev, por ejemplo), el archivo no inserta nada y no falla.
--
-- CON BOLSA, igual que la plataforma (CatalogIntegrationRequestServiceImpl +
-- BudgetService#consumeForPetItemRequest): la bolsa sale del saldo de la wallet del
-- comercial y queda una budget_transaction PET_ITEM_REQUEST por solicitud. Al
-- reservar no se mueve tesorería; lo que queda de cada bolsa lo cuenta
-- KeyBackingCalculator como comprometido, y cada compra pasa $150 de KEYS_RESERVE
-- a OPERATIONS.
--
-- Todo o nada: si la wallet no alcanza para las bolsas de todos los productos que
-- faltan, no se crea ninguno. Así ningún ítem queda sin bolsa, y en cuanto la
-- wallet tenga saldo se crean en el siguiente arranque. Cada bolsa son 100 usos.
--
-- Los sprites están en el bucket de mascotas bajo catalog-sprites/seed/ (PNG de
-- 500×500 con fondo transparente). Sin el archivo, el juego usa su sprite de respaldo.
--
-- Todos los personajes ven todos los ítems: los que vienen de la API no traen
-- allowedPetIds (ni el backend ni RemoteFood en Unity lo soportan), así que la
-- aspirina, la Coca-Cola y la Póker también le salen a las mascotas.
--
-- externalId desde 1000, igual que PetCatalogItemRepository#nextExternalId: por
-- debajo viven los ítems horneados en el build (0–14).
--
-- Idempotente: la solicitud (comercial + nombre del producto) es la llave. Si ya
-- existe, ese producto no se vuelve a crear.
-- ============================================================================

SET @commercial_id := (
    SELECT c.user_id
    FROM users u
    JOIN commercial_details c ON c.user_id = u.id
    WHERE u.public_id = UUID_TO_BIN('0be7a000-0004-0000-0000-000000000003'));

-- 100 usos de pets.commercial-charge-per-use-cents ($150).
SET @bag_cents := 100 * 15000;

SET @wallet_id := (SELECT w.id FROM wallets w WHERE w.commercial_id = @commercial_id);
SET @wallet_balance := (SELECT w.balance_cents FROM wallets w WHERE w.id = @wallet_id FOR UPDATE);

DROP TEMPORARY TABLE IF EXISTS tmp_seed_pet_items;

CREATE TEMPORARY TABLE tmp_seed_pet_items (
    ord             INT          NOT NULL,
    product         VARCHAR(100) NOT NULL,
    description     VARCHAR(500) NOT NULL,
    desired_effects VARCHAR(1000) NOT NULL,
    sprite_key      VARCHAR(255) NOT NULL,
    is_drink        BOOLEAN      NOT NULL,
    is_medicine     BOOLEAN      NOT NULL,
    cures_all       BOOLEAN      NOT NULL,
    price           INT          NOT NULL,
    exp_when_eating INT          NOT NULL,
    health          INT          NOT NULL,
    energy          INT          NOT NULL,
    hunger          INT          NOT NULL,
    thirst          INT          NOT NULL,
    hygiene         INT          NOT NULL,
    humor           INT          NOT NULL,
    body_fat        INT          NOT NULL,
    pending         BOOLEAN      NOT NULL DEFAULT FALSE,
    external_id     INT          NULL
);

-- Precios en llaves, en la escala del catálogo del build (1–100).
INSERT INTO tmp_seed_pet_items (ord, product, description, desired_effects, sprite_key, is_drink, is_medicine, cures_all,
                                price, exp_when_eating, health, energy, hunger, thirst, hygiene, humor, body_fat) VALUES
    (1, 'Coca-Cola', 'Gaseosa bien fría para recargar energía.',
        'Quita la sed y sube energía y humor; suma algo de grasa',
        'catalog-sprites/seed/coca-cola.png', TRUE,  FALSE, FALSE, 12, 3,  0, 10,  0, 15, 0, 10,  5),
    (2, 'Colombiana', 'La gaseosa nuestra, para acompañar cualquier comida.',
        'Quita la sed y sube energía y humor; suma algo de grasa',
        'catalog-sprites/seed/colombiana.png', TRUE, FALSE, FALSE, 12, 3,  0, 10,  0, 15, 0, 10,  5),
    (3, 'Póker', 'Cerveza para compartir.',
        'Sube mucho el humor, quita algo de sed y baja un poco la energía',
        'catalog-sprites/seed/poker.png', TRUE,      FALSE, FALSE, 15, 3,  0, -5,  0, 10, 0, 15,  0),
    (4, 'Aspirina', 'Alivia el malestar y ayuda a recuperarse.',
        'Recupera salud y cura todas las partes del cuerpo',
        'catalog-sprites/seed/aspirina.png', FALSE,  TRUE,  TRUE,  20, 2, 20,  0,  0,  0, 0,  0,  0),
    (5, 'Leche Colanta', 'Leche fresca para crecer fuerte.',
        'Quita la sed y sube salud y energía',
        'catalog-sprites/seed/leche.png', TRUE,      FALSE, FALSE, 10, 4, 10,  5,  0, 15, 0,  0,  0),
    (6, 'Frisby', 'Combo de pollo apanado con papas y arepa.',
        'Llena mucho y sube el humor; suma grasa',
        'catalog-sprites/seed/frisby.png', FALSE,    FALSE, FALSE, 25, 6,  0,  0, 20,  0, 0, 15, 10),
    (7, 'Pedigree', 'Alimento húmedo de carne para perros adultos.',
        'Llena y sube la salud',
        'catalog-sprites/seed/pedigree.png', FALSE,  FALSE, FALSE, 18, 5, 10,  0, 20,  0, 0,  0,  0),
    (8, 'Agility Gold', 'Alimento balanceado para mascotas.',
        'Llena y sube salud y energía',
        'catalog-sprites/seed/agility.png', FALSE,   FALSE, FALSE, 20, 5, 10,  5, 20,  0, 0,  0,  0);

-- 1. Qué productos faltan. Sin comercial o sin wallet no falta ninguno: no hay a
--    quién colgarlos ni de dónde sacar la bolsa.
UPDATE tmp_seed_pet_items t
SET t.pending = @wallet_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM catalog_integration_requests r
    WHERE r.commercial_id = @commercial_id AND r.product_name = t.product);

-- Todo o nada: si el saldo no cubre todas las bolsas, no se crea ninguno.
SET @bags_total := (SELECT COUNT(*) FROM tmp_seed_pet_items WHERE pending) * @bag_cents;
UPDATE tmp_seed_pet_items SET pending = FALSE WHERE @wallet_balance < @bags_total;
SET @bags_total := (SELECT COUNT(*) FROM tmp_seed_pet_items WHERE pending) * @bag_cents;

-- 2. externalId para los que faltan, en orden y a continuación del mayor >= 1000.
SET @next_external_id := (SELECT COALESCE(MAX(external_id), 999) FROM pet_catalog_items WHERE external_id >= 1000);

UPDATE tmp_seed_pet_items
SET external_id = (@next_external_id := @next_external_id + 1)
WHERE pending
ORDER BY ord;

-- 3. Los ítems.
INSERT INTO pet_catalog_items (external_id, name, description, sprite_object_key, is_drink, is_medicine, cures_all_parts,
                               price, active, exp_when_eating, health_delta, energy_delta, hunger_delta, thirst_delta,
                               hygiene_delta, humor_delta, body_fat_delta)
SELECT t.external_id, t.product, t.description, t.sprite_key, t.is_drink, t.is_medicine, t.cures_all,
       t.price, TRUE, t.exp_when_eating, t.health, t.energy, t.hunger, t.thirst, t.hygiene, t.humor, t.body_fat
FROM tmp_seed_pet_items t
WHERE t.external_id IS NOT NULL;

-- 4. Sus solicitudes con la bolsa, enlazadas por el externalId recién asignado.
INSERT INTO catalog_integration_requests (commercial_id, product_name, description, desired_effects, image_object_key,
                                          status, result_catalog_item_id, budget_cents, spent_cents,
                                          created_at, updated_at)
SELECT @commercial_id, t.product, t.description, t.desired_effects, t.sprite_key,
       'COMPLETED', i.id, @bag_cents, 0, NOW(6), NOW(6)
FROM tmp_seed_pet_items t
JOIN pet_catalog_items i ON i.external_id = t.external_id
WHERE t.external_id IS NOT NULL;

-- 5. La reserva de cada bolsa, como la deja BudgetService#consume.
INSERT INTO budget_transactions (wallet_id, amount_cents, type, reference_id, description, created_at)
SELECT @wallet_id, @bag_cents, 'PET_ITEM_REQUEST', CAST(r.id AS CHAR),
       'Presupuesto reservado para ítem en el juego de mascotas', UTC_TIMESTAMP(6)
FROM tmp_seed_pet_items t
JOIN pet_catalog_items i ON i.external_id = t.external_id
JOIN catalog_integration_requests r ON r.result_catalog_item_id = i.id
WHERE t.external_id IS NOT NULL;

-- 6. El descuento en la wallet, con el mismo estado que Wallet#recalculateStatus.
UPDATE wallets
SET balance_cents   = balance_cents - @bags_total,
    status          = IF(balance_cents = 0, 'EXHAUSTED', 'ACTIVE'),
    exhausted_since = IF(balance_cents = 0, COALESCE(exhausted_since, UTC_TIMESTAMP(6)), NULL),
    last_updated    = UTC_TIMESTAMP(6),
    version         = version + 1
WHERE id = @wallet_id AND @bags_total > 0;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_pet_items;
