-- Datos beta de sorteos y marketplace, asociados a los public_id congelados en
-- beta_users.sql. No crea usuarios comerciales adicionales.
--
-- Valores RAW: son placeholders de una sola carga. BetaDataSeeder los cifra con
-- las llaves del ambiente antes de que la aplicación quede disponible.
--
-- El sorteo finalizado y su ganador son un fixture de demo, no un resultado real
-- de Random.org. Las rifas futuras están configuradas con RANDOM_ORG y usan la
-- llave de prueba definida por RANDOM_ORG_BETA_API_KEY en el perfil beta.
--
-- Este seed es idempotente. Si se requiere borrarlo manualmente, eliminar primero
-- las filas hijas y luego sus padres:
-- raffle_winners, raffle_results, raffle_participations, raffle_tickets,
-- raffle_rules, raffle_prizes, raffle_image_assets, raffles, ticket_earning_rules;
-- purchase_items, purchases, product_stock, products, y al final las categorías
-- beta que ya no tengan productos asociados. No hay ON DELETE CASCADE.

-- Categorías del catálogo beta.
INSERT INTO product_category (name, is_active, created_at)
SELECT seed.name, TRUE, NOW()
FROM (
    SELECT 'Moda y accesorios' AS name
    UNION ALL SELECT 'Hogar y tecnología'
) seed
WHERE NOT EXISTS (
    SELECT 1
    FROM product_category existing_category
    WHERE existing_category.name = seed.name
);

-- Catálogo de productos de las marcas beta existentes.
INSERT INTO products (
    approved_at,
    average_rate,
    created_at,
    description,
    max_keys_pct,
    name,
    price_cents,
    review_count,
    status,
    updated_at,
    approved_by,
    commercial_id,
    product_category_id,
    product_type
)
SELECT
    NOW(),
    0,
    NOW(),
    seed.description,
    seed.max_keys_pct,
    seed.product_name,
    seed.price_cents,
    0,
    'ACTIVE',
    NOW(),
    admin_details.user_id,
    commercial_details.user_id,
    product_category.id,
    seed.product_type
FROM (
    SELECT
        '0be7a000-0004-0000-0000-000000000002' AS commercial_public_id,
        'Moda y accesorios' AS category_name,
        'Tarjeta digital Moda Urbana - $85.000 COP' AS product_name,
        'Bono digital de uso único para compras en Moda Urbana Colombia.' AS description,
        8500000 AS price_cents,
        35 AS max_keys_pct,
        'DIGITAL' AS product_type
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000003',
        'Hogar y tecnología',
        'Kit de iluminación solar Andina - 2 unidades',
        'Dos lámparas solares compactas para balcón o terraza, con garantía de la tienda.',
        11990000,
        50,
        'PHYSICAL'
    UNION ALL SELECT
        '0be7a000-0004-0000-0000-000000000001',
        'Supermercados',
        'Caja de panadería artesanal Trigo Dorado - 12 piezas',
        'Selección surtida de panes artesanales horneados el mismo día en Bogotá.',
        4850000,
        20,
        'PHYSICAL'
) seed
JOIN users commercial_user
  ON commercial_user.public_id = UUID_TO_BIN(seed.commercial_public_id)
JOIN commercial_details
  ON commercial_details.user_id = commercial_user.id
JOIN users admin_user
  ON admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
JOIN admin_details
  ON admin_details.user_id = admin_user.id
JOIN product_category
  ON product_category.name = seed.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM products existing_product
    WHERE existing_product.commercial_id = commercial_details.user_id
      AND existing_product.name = seed.product_name
);

-- Compras completadas por consumidores activos de beta.
INSERT INTO purchases (
    cash_cents,
    commission_cents,
    commission_vat_cents,
    completed_at,
    created_at,
    delivery_email,
    delivery_email_verified,
    keys_value_cents,
    net_to_commercials_cents,
    prosperity_absorbed_cents,
    reference_id,
    status,
    total_cents,
    updated_at,
    consumer_id
)
SELECT
    seed.total_cents,
    seed.commission_cents,
    0,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    consumer_user.email,
    TRUE,
    0,
    seed.total_cents - seed.commission_cents,
    0,
    seed.reference_id,
    'COMPLETED',
    seed.total_cents,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    consumer_details.user_id
FROM (
    SELECT
        'BETA-MARKETPLACE-DIGITAL-001' AS reference_id,
        '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id,
        8500000 AS total_cents,
        850000 AS commission_cents,
        4 AS days_ago
    UNION ALL SELECT
        'BETA-MARKETPLACE-PHYSICAL-001',
        '0be7a000-0005-0000-0000-000000000005',
        11990000,
        1199000,
        2
) seed
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN consumer_details
  ON consumer_details.user_id = consumer_user.id
WHERE NOT EXISTS (
    SELECT 1
    FROM purchases existing_purchase
    WHERE existing_purchase.reference_id = seed.reference_id
);

-- El inventario asociado a las compras: un código digital entregado y un
-- producto físico vendido, pendiente de validación del PIN en tienda.
INSERT INTO product_stock (
    code,
    code_hash,
    created_at,
    sold_at,
    status,
    updated_at,
    version,
    product_id
)
SELECT
    CONCAT('RAW:', seed.plain_code),
    SHA2(seed.plain_code, 256),
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    seed.stock_status,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    0,
    product.id
FROM (
    SELECT
        'Tarjeta digital Moda Urbana - $85.000 COP' AS product_name,
        'BETA-MARKETPLACE-DIGITAL-001' AS purchase_reference,
        'BETA-MODA-URBANA-85K-2026-001' AS plain_code,
        'SOLD' AS stock_status,
        4 AS days_ago
    UNION ALL SELECT
        'Kit de iluminación solar Andina - 2 unidades',
        'BETA-MARKETPLACE-PHYSICAL-001',
        'BETA-ECOSISTEMA-KIT-SOLAR-2026-001',
        'SOLD',
        2
) seed
JOIN products product
  ON product.name = seed.product_name
WHERE NOT EXISTS (
    SELECT 1
    FROM product_stock existing_stock
    WHERE existing_stock.product_id = product.id
      AND existing_stock.code_hash = SHA2(seed.plain_code, 256)
)
AND NOT EXISTS (
    SELECT 1
    FROM purchase_items existing_item
    JOIN purchases existing_purchase
      ON existing_purchase.id = existing_item.purchase_id
    WHERE existing_purchase.reference_id = seed.purchase_reference
      AND existing_item.product_id = product.id
);

-- Una unidad disponible del producto de Trigo Dorado. La existencia de
-- cualquier fila de inventario impide duplicarla en ejecuciones posteriores.
INSERT INTO product_stock (
    code,
    code_hash,
    created_at,
    status,
    version,
    product_id
)
SELECT
    'RAW:BETA-TRIGO-DORADO-CAJA-2026-001',
    SHA2('BETA-TRIGO-DORADO-CAJA-2026-001', 256),
    NOW(),
    'AVAILABLE',
    0,
    product.id
FROM products product
WHERE product.name = 'Caja de panadería artesanal Trigo Dorado - 12 piezas'
  AND NOT EXISTS (
      SELECT 1
      FROM product_stock existing_stock
      WHERE existing_stock.product_id = product.id
  );

-- Historial de compra: el bono digital ya se reclamó; el pedido físico espera
-- la validación del PIN por el comercio.
INSERT INTO purchase_items (
    commercial_id,
    commission_cents,
    commission_pct_applied,
    commission_vat_cents,
    commercial_activity_type_at_purchase,
    commission_base_cents,
    created_at,
    delivered_at,
    delivered_code,
    max_keys_pct_at_purchase,
    net_to_commercial_cents,
    product_name_snapshot,
    prosperity_absorbed_cents,
    status,
    subtotal_cents,
    unit_price_cents,
    product_stock_id,
    product_id,
    purchase_id,
    claim_attempts,
    claimed_at,
    claim_pin_hash,
    claim_expires_at
)
SELECT
    product.commercial_id,
    seed.commission_cents,
    10,
    0,
    'PRODUCTS',
    seed.total_cents,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    CASE WHEN seed.delivered_code IS NULL
         THEN NULL ELSE CONCAT('RAW:', seed.delivered_code) END,
    product.max_keys_pct,
    seed.total_cents - seed.commission_cents,
    product.name,
    0,
    seed.item_status,
    seed.total_cents,
    seed.total_cents,
    product_stock.id,
    product.id,
    purchase.id,
    0,
    CASE WHEN seed.item_status = 'CLAIMED'
         THEN DATE_SUB(NOW(), INTERVAL seed.days_ago DAY) ELSE NULL END,
    CASE WHEN seed.claim_pin IS NULL
         THEN NULL ELSE CONCAT('RAW:', seed.claim_pin) END,
    CASE WHEN seed.claim_pin IS NULL
         THEN NULL ELSE DATE_ADD(NOW(), INTERVAL 13 DAY) END
FROM (
    SELECT
        'BETA-MARKETPLACE-DIGITAL-001' AS reference_id,
        'Tarjeta digital Moda Urbana - $85.000 COP' AS product_name,
        'BETA-MODA-URBANA-85K-2026-001' AS plain_stock_code,
        'BETA-MODA-URBANA-85K-2026-001' AS delivered_code,
        NULL AS claim_pin,
        'CLAIMED' AS item_status,
        8500000 AS total_cents,
        850000 AS commission_cents,
        4 AS days_ago
    UNION ALL SELECT
        'BETA-MARKETPLACE-PHYSICAL-001',
        'Kit de iluminación solar Andina - 2 unidades',
        'BETA-ECOSISTEMA-KIT-SOLAR-2026-001',
        'BETA-ECOSISTEMA-KIT-SOLAR-2026-001',
        '246810',
        'PENDING',
        11990000,
        1199000,
        2
) seed
JOIN purchases purchase
  ON purchase.reference_id = seed.reference_id
JOIN products product
  ON product.name = seed.product_name
JOIN product_stock
  ON product_stock.product_id = product.id
 AND product_stock.code_hash = SHA2(seed.plain_stock_code, 256)
WHERE NOT EXISTS (
    SELECT 1
    FROM purchase_items existing_item
    WHERE existing_item.purchase_id = purchase.id
      AND existing_item.product_id = product.id
);

-- Rifas: una activa, una próxima en DRAFT y una finalizada con ganador.
INSERT INTO raffles (
    title,
    description,
    raffle_type,
    raffle_status,
    start_date,
    end_date,
    draw_date,
    max_tickets_per_user,
    max_total_tickets,
    total_tickets_issued,
    total_participants,
    draw_method,
    created_at,
    updated_at,
    created_by,
    terms_and_conditions
)
SELECT
    seed.title,
    seed.description,
    'STANDARD',
    seed.raffle_status,
    seed.start_date,
    seed.end_date,
    seed.draw_date,
    20,
    1000,
    0,
    0,
    'RANDOM_ORG',
    NOW(),
    NOW(),
    admin_user.id,
    'Participación de demostración beta. Aplican términos y condiciones publicados por VeryGana.'
FROM (
    SELECT
        'Mercado local: sabores de Colombia' AS title,
        'Participa por una selección de café de origen y productos de despensa de marcas colombianas.' AS description,
        'ACTIVE' AS raffle_status,
        DATE_SUB(NOW(), INTERVAL 3 DAY) AS start_date,
        DATE_ADD(NOW(), INTERVAL 18 DAY) AS end_date,
        DATE_ADD(NOW(), INTERVAL 19 DAY) AS draw_date
    UNION ALL SELECT
        'Tecnología para un hogar más eficiente',
        'Próximo sorteo beta por un kit solar para espacios pequeños.',
        'DRAFT',
        DATE_ADD(NOW(), INTERVAL 3 DAY),
        DATE_ADD(NOW(), INTERVAL 33 DAY),
        DATE_ADD(NOW(), INTERVAL 34 DAY)
    UNION ALL SELECT
        'Experiencia de café de altura',
        'Sorteo de demostración ya finalizado con un ganador registrado.',
        'COMPLETED',
        DATE_SUB(NOW(), INTERVAL 35 DAY),
        DATE_SUB(NOW(), INTERVAL 8 DAY),
        DATE_SUB(NOW(), INTERVAL 7 DAY)
) seed
JOIN users admin_user
  ON admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
WHERE NOT EXISTS (
    SELECT 1
    FROM raffles existing_raffle
    WHERE existing_raffle.title = seed.title
);

-- Premios de las tres rifas. claim_code se protege en BetaDataSeeder.
INSERT INTO raffle_prizes (
    brand,
    claim_code,
    claim_instructions,
    claimed_count,
    created_at,
    description,
    position,
    prize_status,
    prize_type,
    quantity,
    title,
    updated_at,
    value,
    raffle_id
)
SELECT
    seed.brand,
    CONCAT('RAW:', seed.claim_code),
    seed.claim_instructions,
    0,
    NOW(),
    seed.description,
    1,
    'PENDING',
    seed.prize_type,
    1,
    seed.prize_title,
    NOW(),
    seed.value,
    raffle.id
FROM (
    SELECT
        'Mercado local: sabores de Colombia' AS raffle_title,
        'Café Niebla Clara' AS brand,
        'Bono de mercado local por $250.000 COP' AS prize_title,
        'Bono digital ficticio para canjear por café y productos colombianos.' AS description,
        'DIGITAL' AS prize_type,
        250000.00 AS value,
        'BETA-RAFFLE-ACTIVE-CLAIM-2026-001' AS claim_code,
        'El código beta se valida en la pantalla de reclamo del premio.' AS claim_instructions
    UNION ALL SELECT
        'Tecnología para un hogar más eficiente',
        'Lumen Andino',
        'Kit solar de balcón',
        'Kit de iluminación solar de muestra para el hogar.',
        'PHYSICAL',
        380000.00,
        'BETA-RAFFLE-UPCOMING-CLAIM-2026-001',
        'Coordina la entrega física con el equipo beta.'
    UNION ALL SELECT
        'Experiencia de café de altura',
        'Tostadora Bruma Alta',
        'Experiencia de café para dos',
        'Premio ficticio de la demo beta; el resultado no fue generado por Random.org.',
        'DIGITAL',
        180000.00,
        'BETA-RAFFLE-COMPLETED-CLAIM-2026-001',
        'Premio de demostración pendiente de reclamo.'
) seed
JOIN raffles raffle
  ON raffle.title = seed.raffle_title
WHERE NOT EXISTS (
    SELECT 1
    FROM raffle_prizes existing_prize
    WHERE existing_prize.raffle_id = raffle.id
      AND existing_prize.title = seed.prize_title
);

-- Regla para ganar un ticket por inicio diario; la próxima rifa se activa
-- automáticamente cuando llegue su start_date, pues ya tiene premio y regla.
INSERT INTO ticket_earning_rules (
    rule_name,
    description,
    rule_type,
    is_active,
    priority,
    min_purchase_amount_cents,
    daily_login,
    referral_added_quantity,
    tickets_to_award,
    created_at,
    updated_at,
    created_by
)
SELECT
    'BETA - Inicio diario',
    'Regla de demostración beta para rifas configuradas con Random.org.',
    'DAILY_LOGIN',
    TRUE,
    10,
    NULL,
    TRUE,
    NULL,
    1,
    NOW(),
    NOW(),
    admin_user.id
FROM users admin_user
WHERE admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
  AND NOT EXISTS (
      SELECT 1
      FROM ticket_earning_rules existing_rule
      WHERE existing_rule.rule_name = 'BETA - Inicio diario'
  );

INSERT INTO ticket_earning_rules (
    rule_name,
    description,
    rule_type,
    is_active,
    priority,
    min_purchase_amount_cents,
    daily_login,
    referral_added_quantity,
    tickets_to_award,
    created_at,
    updated_at,
    created_by
)
SELECT
    'BETA - Compra marketplace',
    'Ticket de demostración por compra elegible en marketplace.',
    'PURCHASE',
    TRUE,
    20,
    500000,
    FALSE,
    NULL,
    1,
    NOW(),
    NOW(),
    admin_user.id
FROM users admin_user
WHERE admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
  AND NOT EXISTS (
      SELECT 1
      FROM ticket_earning_rules existing_rule
      WHERE existing_rule.rule_name = 'BETA - Compra marketplace'
  );

INSERT INTO raffle_rules (
    raffle_id,
    rule_id,
    is_active,
    max_tickets_by_source,
    current_tickets_by_source,
    created_at,
    updated_at,
    created_by
)
SELECT
    raffle.id,
    earning_rule.id,
    TRUE,
    300,
    0,
    NOW(),
    NOW(),
    admin_user.id
FROM raffles raffle
JOIN ticket_earning_rules earning_rule
  ON earning_rule.rule_name IN (
      'BETA - Inicio diario',
      'BETA - Compra marketplace'
  )
JOIN users admin_user
  ON admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
WHERE raffle.title IN (
    'Mercado local: sabores de Colombia',
    'Tecnología para un hogar más eficiente'
)
  AND NOT EXISTS (
      SELECT 1
      FROM raffle_rules existing_rule
      WHERE existing_rule.raffle_id = raffle.id
        AND existing_rule.rule_id = earning_rule.id
  );

-- Tickets de muestra vinculados a las compras beta ya completadas.
INSERT INTO raffle_tickets (
    is_winner,
    issued_at,
    source,
    source_id,
    status,
    ticket_number,
    raffle_id,
    ticket_owner_id
)
SELECT
    seed.is_winner,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    'PURCHASE',
    purchase.id,
    'ACTIVE',
    seed.ticket_number,
    raffle.id,
    consumer_details.user_id
FROM (
    SELECT
        'Mercado local: sabores de Colombia' AS raffle_title,
        'BETA-MARKETPLACE-DIGITAL-001' AS purchase_reference,
        '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id,
        'BETA-MERCADO-0001' AS ticket_number,
        FALSE AS is_winner,
        3 AS days_ago
    UNION ALL SELECT
        'Mercado local: sabores de Colombia',
        'BETA-MARKETPLACE-PHYSICAL-001',
        '0be7a000-0005-0000-0000-000000000005',
        'BETA-MERCADO-0002',
        FALSE,
        2
    UNION ALL SELECT
        'Experiencia de café de altura',
        'BETA-MARKETPLACE-DIGITAL-001',
        '0be7a000-0005-0000-0000-000000000004',
        'BETA-CAFE-0001',
        FALSE,
        10
    UNION ALL SELECT
        'Experiencia de café de altura',
        'BETA-MARKETPLACE-PHYSICAL-001',
        '0be7a000-0005-0000-0000-000000000005',
        'BETA-CAFE-0002',
        TRUE,
        9
) seed
JOIN raffles raffle
  ON raffle.title = seed.raffle_title
JOIN purchases purchase
  ON purchase.reference_id = seed.purchase_reference
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN consumer_details
  ON consumer_details.user_id = consumer_user.id
WHERE NOT EXISTS (
    SELECT 1
    FROM raffle_tickets existing_ticket
    WHERE existing_ticket.raffle_id = raffle.id
      AND existing_ticket.ticket_number = seed.ticket_number
);

UPDATE raffle_rules raffle_rule
JOIN raffles raffle
  ON raffle.id = raffle_rule.raffle_id
JOIN ticket_earning_rules earning_rule
  ON earning_rule.id = raffle_rule.rule_id
SET raffle_rule.current_tickets_by_source = (
    SELECT COUNT(*)
    FROM raffle_tickets ticket
    WHERE ticket.raffle_id = raffle.id
      AND ticket.source = earning_rule.rule_type
)
WHERE raffle.title IN (
    'Mercado local: sabores de Colombia',
    'Tecnología para un hogar más eficiente'
);

INSERT INTO raffle_participations (
    first_participation_at,
    last_participation_at,
    tickets_count,
    consumer_id,
    raffle_id
)
SELECT
    MIN(ticket.issued_at),
    MAX(ticket.issued_at),
    COUNT(*),
    ticket.ticket_owner_id,
    ticket.raffle_id
FROM raffle_tickets ticket
JOIN raffles raffle
  ON raffle.id = ticket.raffle_id
WHERE raffle.title IN (
    'Mercado local: sabores de Colombia',
    'Experiencia de café de altura'
)
GROUP BY ticket.raffle_id, ticket.ticket_owner_id
ON DUPLICATE KEY UPDATE
    first_participation_at = VALUES(first_participation_at),
    last_participation_at = VALUES(last_participation_at),
    tickets_count = VALUES(tickets_count);

-- Resultado final de demo. Se declara explícitamente que no hubo llamada al
-- proveedor: RANDOM_ORG queda como método configurado, no como prueba del fixture.
INSERT INTO raffle_results (
    draw_proof,
    drawn_at,
    external_reference,
    raffle_id
)
SELECT
    CONCAT(
        '{"source":"BETA_SEED_FIXTURE","raffleTitle":"',
        raffle.title,
        '","configuredDrawMethod":"RANDOM_ORG","actualDrawMethod":"BETA_SEED_FIXTURE",',
        '"drawMethodNote":"Ganador ficticio de demo; no se ejecuto Random.org."}'
    ),
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    NULL,
    raffle.id
FROM raffles raffle
WHERE raffle.title = 'Experiencia de café de altura'
  AND NOT EXISTS (
      SELECT 1
      FROM raffle_results existing_result
      WHERE existing_result.raffle_id = raffle.id
  );

INSERT INTO raffle_winners (
    claim_deadline,
    created_at,
    prize_claimed,
    prize_id,
    raffle_result_id,
    winner_consumer_id,
    winning_ticket_id
)
SELECT
    DATE_ADD(NOW(), INTERVAL 23 DAY),
    DATE_SUB(NOW(), INTERVAL 7 DAY),
    FALSE,
    prize.id,
    raffle_result.id,
    consumer_details.user_id,
    winning_ticket.id
FROM raffles raffle
JOIN raffle_results raffle_result
  ON raffle_result.raffle_id = raffle.id
JOIN raffle_prizes prize
  ON prize.raffle_id = raffle.id
 AND prize.title = 'Experiencia de café para dos'
JOIN raffle_tickets winning_ticket
  ON winning_ticket.raffle_id = raffle.id
 AND winning_ticket.ticket_number = 'BETA-CAFE-0002'
JOIN consumer_details
  ON consumer_details.user_id = winning_ticket.ticket_owner_id
WHERE raffle.title = 'Experiencia de café de altura'
  AND NOT EXISTS (
      SELECT 1
      FROM raffle_winners existing_winner
      WHERE existing_winner.prize_id = prize.id
         OR existing_winner.winning_ticket_id = winning_ticket.id
  );

UPDATE raffles raffle
SET raffle.total_tickets_issued = (
        SELECT COUNT(*)
        FROM raffle_tickets ticket
        WHERE ticket.raffle_id = raffle.id
    ),
    raffle.total_participants = (
        SELECT COUNT(DISTINCT ticket.ticket_owner_id)
        FROM raffle_tickets ticket
        WHERE ticket.raffle_id = raffle.id
    )
WHERE raffle.title IN (
    'Mercado local: sabores de Colombia',
    'Tecnología para un hogar más eficiente',
    'Experiencia de café de altura'
);
