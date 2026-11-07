-- Imágenes de las categorías de producto de productCategories.sql.
-- Reutiliza las claves de db/seed/test/test-productCategoryImageAsset.sql (dev): los
-- archivos tienen que existir en el bucket de R2 que use beta (R2_BUCKET_NAME).
-- Faltan "Moda y accesorios" y "Hogar y tecnología", que no tienen imagen en ningún seed.
-- Se asocia por nombre y no por id. Idempotente: object_key es único.
INSERT INTO product_category_image_assets (mime_type, object_key, size_bytes, status, uploaded_at, product_category_id)
SELECT seed.mime_type, seed.object_key, seed.size_bytes, 'VALIDATED', NOW(), product_category.id
FROM (
    SELECT 'Videojuegos' AS category_name, 'IMAGE_PNG' AS mime_type,
           'products-categories/1779396211544-4548d464.png' AS object_key, 784648 AS size_bytes
    UNION ALL SELECT 'Supermercados',   'IMAGE_PNG', 'products-categories/1779396233606-1923b71e.png', 863263
    UNION ALL SELECT 'Música',          'IMAGE_PNG', 'products-categories/1779396267221-6bdd304c.png', 881544
    UNION ALL SELECT 'Entretenimiento', 'IMAGE_PNG', 'products-categories/1779407497311-db95bc01.png', 792568
    UNION ALL SELECT 'Fitness',         'IMAGE_PNG', 'products-categories/1779407516106-0d0b29cb.png', 929853
) seed
JOIN product_category
  ON product_category.name = seed.category_name
WHERE NOT EXISTS (
    SELECT 1
    FROM product_category_image_assets existing_asset
    WHERE existing_asset.object_key = seed.object_key
       OR existing_asset.product_category_id = product_category.id
);
