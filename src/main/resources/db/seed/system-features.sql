INSERT INTO system_features
(feature_key, endpoint_prefix, status, category, description)
VALUES

-- ============================================================
-- MONETIZACION: ads, surveys, raffles, games, marketplace, campaigns
-- ============================================================

('ADS_SYSTEM',
'/ads',
'ENABLED',
'MONETIZACION',
'Sistema de anuncios: visualización, interacción y assets'),

('SURVEYS_SYSTEM',
'/surveys',
'ENABLED',
'MONETIZACION',
'Sistema de encuestas: participación y recompensas'),

('RAFFLES_SYSTEM',
'/api/raffles',
'ENABLED',
'MONETIZACION',
'Sistema de rifas: tickets, resultados y ganadores'),

('GAMES_SYSTEM',
'/games',
'ENABLED',
'MONETIZACION',
'Sistema de juegos y minijuegos'),

('MARKETPLACE',
'/products',
'ENABLED',
'MONETIZACION',
'Marketplace: catálogo, compras y reseñas de productos'),

('CAMPAIGNS_SYSTEM',
'/campaigns',
'ENABLED',
'MONETIZACION',
'Gestión de campañas publicitarias de comerciales'),

-- ============================================================
-- IMPACTO
-- ============================================================

('IMPACT_STORIES_SYSTEM',
'/impact-stories',
'ENABLED',
'IMPACTO',
'Historias de impacto social: publicación y medios'),

-- ============================================================
-- FINANCIERO
-- ============================================================

('WALLET_SYSTEM',
'/commercial/wallet',
'ENABLED',
'FINANCIERO',
'Billetera y saldo de cuentas comerciales'),

('KEY_TRANSACTIONS',
'/consumer/transactions',
'ENABLED',
'FINANCIERO',
'Movimientos y transacciones de llaves de consumidores'),

('PLANS_SYSTEM',
'/plans',
'ENABLED',
'FINANCIERO',
'Planes y suscripciones para comerciales'),

-- ============================================================
-- ADQUISICION
-- ============================================================

('USER_REGISTRATION',
'/auth/register',
'ENABLED',
'ADQUISICION',
'Registro de nuevos usuarios (consumidores y comerciales)'),

('REFERRAL_SYSTEM',
'/referrals',
'ENABLED',
'ADQUISICION',
'Programa de referidos'),

-- ============================================================
-- NOTIFICACIONES
-- ============================================================

('NOTIFICATIONS_SYSTEM',
'/notifications',
'ENABLED',
'NOTIFICACIONES',
'Notificaciones en tiempo real para usuarios'),

-- ============================================================
-- PERFILES
-- ============================================================

('CONSUMER_PROFILES',
'/consumers',
'ENABLED',
'PERFILES',
'Perfiles y configuración de consumidores'),

('COMMERCIAL_PROFILES',
'/commercials',
'ENABLED',
'PERFILES',
'Perfiles y configuración de comerciales'),

-- ============================================================
-- ADMINISTRACION
-- ============================================================

('ADMIN_PANEL',
'/api/admin',
'ENABLED',
'ADMINISTRACION',
'Panel de administración general')

ON DUPLICATE KEY UPDATE
status      = VALUES(status),
category    = VALUES(category),
description = VALUES(description);
