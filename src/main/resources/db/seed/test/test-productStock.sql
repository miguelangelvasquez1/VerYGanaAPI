INSERT INTO product_stock (id, code, created_at, expiration_date, sold_at, status, updated_at, version, product_id)
VALUES
    (1,  'PLAY-YUHJ',    NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 1),
    (2,  'PLAY-43HM',    NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 1),
    (3,  'PLAY-TRFB',    NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 1),
    (4,  'SPOTIFY-HJLK', NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 2),
    (5,  'SPOTIFY-HPOY', NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 2),
    (6,  'SPOTIFY-5RTH', NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 2),
    (7,  'NETFLIX-GHMK', NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 3),
    (8,  'NETFLIX-ADSC', NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 3),
    (9,  'NETFLIX-YU78', NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 3),
    (10, 'ARA-32FB',     NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 4),
    (11, 'ARA-54FC',     NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 4),
    (12, 'ARA-78KL',     NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 4),
    (13, 'SMART-56GH',   NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 5),
    (14, 'SMART-TGKM',   NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 5),
    (15, 'SMART-S3DF',   NOW(), NULL, NULL, 'AVAILABLE', NOW(), 0, 5)

ON DUPLICATE KEY UPDATE id = VALUES(id);
