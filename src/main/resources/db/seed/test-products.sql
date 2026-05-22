INSERT INTO products (id, approved_at, average_rate, created_at, deleted_at, deletion_reason, description, 
max_keys_pct, name, price_cents, rejected_at, rejected_until, rejection_reason, resubmission_count, 
resubmitted_at, review_count, status, updated_at, approved_by, commercial_id, deleted_by, product_category_id, 
rejected_by)

VALUES
    (1, NOW(), 0, NOW(), NULL, NULL, 'Membresia de 3 meses PlayStation plus', 50, 'Membresia Play Station Plus', 8990000, NULL, NULL, NULL, NULL, NULL, 0, 'ACTIVE',
    NOW(), 1, 2, NULL, 1, NULL),
    (2, NOW(), 0, NOW(), NULL, NULL, 'Disfruta de la mejor música en spotify por un mes.', 50, 'Membresia de spotify', 3190000, NULL, NULL, NULL, NULL, NULL, 0, 'ACTIVE',
    NOW(), 1, 2, NULL, 3, NULL),
    (3, NOW(), 0, NOW(), NULL, NULL, 'Disfruta de las mejores series y pelicula en la plataforma netflix por 2 meses.', 50, 'Membresia Netflix', 4790000, NULL, NULL, NULL, NULL, NULL, 0, 'ACTIVE',
    NOW(), 1, 2, NULL, 6, NULL),
    (4, NOW(), 0, NOW(), NULL, NULL, 'Aprovecha y compra bonos canjeables en todos las tiendas ara de pais.', 50, 'Bono de mercado ara', 5000000, NULL, NULL, NULL, NULL, NULL, 0, 'ACTIVE',
    NOW(), 1, 2, NULL, 2, NULL),
    (5, NOW(), 0, NOW(), NULL, NULL, 'Disfruta del plan black por 1 mes en cualquiera de los gimnasios smart fit del país.', 50, 'Plan black Smart fit', 5490000, NULL, NULL, NULL, NULL, NULL, 0, 'ACTIVE',
    NOW(), 1, 2, NULL, 7, NULL)

ON DUPLICATE KEY UPDATE id = VALUES(id);
