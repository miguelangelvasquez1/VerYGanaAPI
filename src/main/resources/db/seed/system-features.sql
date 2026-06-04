INSERT INTO system_features
(feature_key, endpoint_prefix, status, category, description)
VALUES

-- ============================================================
-- MONETIZACIÓN PRINCIPAL
-- ============================================================

('ADS_SYSTEM',
'/ads',
'ENABLED',
'MONETIZATION',
'Sistema de anuncios: visualización, interacción y assets'),

('SURVEYS_SYSTEM',
'/surveys',
'ENABLED',
'MONETIZATION',
'Sistema de encuestas: participación y recompensas'),

('RAFFLES_SYSTEM',
'/api/raffles',
'ENABLED',
'MONETIZATION',
'Sistema de rifas y sorteos: compra de tickets y participación'),

('RAFFLE_RESULTS',
'/api/results',
'ENABLED',
'MONETIZATION',
'Resultados de sorteos'),

('RAFFLE_WINNERS',
'/api/winners',
'ENABLED',
'MONETIZATION',
'Ganadores de sorteos'),

('RAFFLE_TICKETS',
'/api/my/raffle-tickets',
'ENABLED',
'MONETIZATION',
'Mis tickets de rifa'),

('GAMES_SYSTEM',
'/games',
'ENABLED',
'MONETIZATION',
'Sistema de juegos y minijuegos'),

-- ============================================================
-- MARKETPLACE
-- ============================================================

('MARKETPLACE_CATALOG',
'/products',
'ENABLED',
'MARKETPLACE',
'Catálogo de productos: listado y detalle'),

('MARKETPLACE_PURCHASES',
'/purchases',
'ENABLED',
'MARKETPLACE',
'Flujo de compras en marketplace'),

('MARKETPLACE_REVIEWS',
'/productsReviews',
'ENABLED',
'MARKETPLACE',
'Reseñas y calificaciones de productos'),

-- ============================================================
-- SISTEMA FINANCIERO
-- ============================================================

('WALLET_SYSTEM',
'/commercial/wallet',
'ENABLED',
'FINANCIAL',
'Billetera y saldo de cuentas comerciales'),

('KEY_TRANSACTIONS',
'/consumer/transactions',
'ENABLED',
'FINANCIAL',
'Movimientos y transacciones de llaves de consumidores'),

('PLANS_SYSTEM',
'/plans',
'ENABLED',
'FINANCIAL',
'Planes y suscripciones para comerciales'),

-- ============================================================
-- ADQUISICIÓN DE USUARIOS
-- ============================================================

('USER_REGISTRATION',
'/auth/register',
'ENABLED',
'USER_ACQUISITION',
'Registro de nuevos usuarios (consumidores y comerciales)'),

('REFERRAL_SYSTEM',
'/referrals',
'ENABLED',
'USER_ACQUISITION',
'Programa de referidos'),

-- ============================================================
-- ENGAGEMENT Y COMUNICACIÓN
-- ============================================================

('NOTIFICATIONS_SYSTEM',
'/notifications',
'ENABLED',
'ENGAGEMENT',
'Notificaciones en tiempo real para usuarios'),

('CAMPAIGNS_SYSTEM',
'/campaigns',
'ENABLED',
'ENGAGEMENT',
'Gestión de campañas publicitarias de comerciales'),

('IMPACT_STORIES_SYSTEM',
'/impact-stories',
'ENABLED',
'ENGAGEMENT',
'Historias de impacto social: publicación y medios'),

-- ============================================================
-- PERFILES DE USUARIO
-- ============================================================

('CONSUMER_PROFILES',
'/consumers',
'ENABLED',
'USER_PROFILES',
'Perfiles y configuración de consumidores'),

('COMMERCIAL_PROFILES',
'/commercials',
'ENABLED',
'USER_PROFILES',
'Perfiles y configuración de comerciales'),

-- ============================================================
-- ADMINISTRACIÓN
-- Un solo prefijo cubre /api/admin/raffles, /api/admin/treasury,
-- /api/admin/tickets, /api/admin/payouts, /api/admin/ticket-rules.
-- ============================================================

('ADMIN_PANEL',
'/api/admin',
'ENABLED',
'ADMINISTRATION',
'Panel de administración general')

ON DUPLICATE KEY UPDATE
feature_key = feature_key;