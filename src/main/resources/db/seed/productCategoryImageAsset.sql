INSERT INTO product_category_image_assets (mime_type, object_key, size_bytes, status, uploaded_at, product_category_id)
VALUES
    ('IMAGE_PNG', 'products-categories/1779396211544-4548d464.png', 784648, 'VALIDATED', NOW(), 1),
    ('IMAGE_PNG', 'products-categories/1779396233606-1923b71e.png', 863263, 'VALIDATED', NOW(), 2),
    ('IMAGE_PNG', 'products-categories/1779396267221-6bdd304c.png', 881544, 'VALIDATED', NOW(), 3),
    ('IMAGE_PNG', 'products-categories/1779407497311-db95bc01.png', 792568, 'VALIDATED', NOW(), 6),
    ('IMAGE_PNG', 'products-categories/1779407516106-0d0b29cb.png', 929853, 'VALIDATED', NOW(), 7);

ON DUPLICATE KEY UPDATE product_category_id = product_category_id;