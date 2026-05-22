INSERT INTO product_categories (id, name, description, created_at, updated_at)
VALUES
    (1, 'Videojuegos',      true, NOW(), NOW()),
    (2, 'Supermercados',    true, NOW(), NOW()),
    (3, 'Música',           true, NOW(), NOW()),
    (6, 'Entretenimiento',  true, NOW(), NOW()),
    (7, 'Fitness',          true, NOW(), NOW())

ON DUPLICATE KEY UPDATE name = name;