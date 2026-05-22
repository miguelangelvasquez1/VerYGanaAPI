INSERT INTO product_image_assets (mime_type, object_key, size_bytes, status, uploaded_at, product_id)
VALUES
    ('IMAGE_PNG',  'products/commercial-2/1779407655456-52126342.png',  24520, 'VALIDATED', NOW(), 1),
    ('IMAGE_JPEG', 'products/commercial-2/1779412957370-daaebe1b.jpg',  26480, 'VALIDATED', NOW(), 2),
    ('IMAGE_PNG',  'products/commercial-2/1779413069127-850ba6b4.png',  68558, 'VALIDATED', NOW(), 3),
    ('IMAGE_WEBP', 'products/commercial-2/1779413241417-f731c8b5.webp',  9508, 'VALIDATED', NOW(), 4),
    ('IMAGE_JPEG', 'products/commercial-2/1779413566443-831fcdd7.jpg',   7180, 'VALIDATED', NOW(), 5)

ON DUPLICATE KEY UPDATE product_id = VALUES(product_id);
