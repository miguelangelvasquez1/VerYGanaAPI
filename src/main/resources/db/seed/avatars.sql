INSERT INTO avatars (name, image_url, is_active, sort_order)
VALUES ('strawberry', 'https://cdn.verygana.com/public/profile-images/strawberry.png', true, 1)
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO avatars (name, image_url, is_active, sort_order)
VALUES ('pineapple', 'https://cdn.verygana.com/public/profile-images/pineapple.png', true, 2)
ON DUPLICATE KEY UPDATE name = name;

INSERT INTO avatars (name, image_url, is_active, sort_order)
VALUES ('blueberry', 'https://cdn.verygana.com/public/profile-images/blueberry.png', true, 2)
ON DUPLICATE KEY UPDATE name = name;