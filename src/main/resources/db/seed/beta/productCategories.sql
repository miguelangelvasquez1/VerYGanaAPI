INSERT INTO product_category (id, name, is_active, created_at)
VALUES
    (1, 'Videojuegos',     true, NOW()),
    (2, 'Supermercados',   true, NOW()),
    (3, 'Música',          true, NOW()),
    (6, 'Entretenimiento', true, NOW()),
    (7, 'Fitness',         true, NOW())

ON DUPLICATE KEY UPDATE name = name;