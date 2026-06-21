INSERT INTO system_features
(feature_key, endpoint_prefix, status, category, description)
VALUES

-- ============================================================
-- MONETIZATION: ads, surveys, raffles, games, campaigns
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
 '/results',
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

('CAMPAIGNS_SYSTEM',
 '/campaigns',
 'ENABLED',
 'MONETIZATION',
 'Gestión de campañas publicitarias de comerciales'),

-- ============================================================
-- MARKETPLACE
-- ============================================================

('MARKETPLACE',
 '/products',
 'ENABLED',
 'MARKETPLACE',
 'Marketplace: catálogo, compras y reseñas de productos'),

-- ============================================================
-- ENGAGEMENT: impact stories, notifications
-- ============================================================

('IMPACT_STORIES_SYSTEM',
 '/impact-stories',
 'ENABLED',
 'ENGAGEMENT',
 'Historias de impacto social: publicación y medios'),

('NOTIFICATIONS_SYSTEM',
 '/notifications',
 'ENABLED',
 'ENGAGEMENT',
 'Notificaciones en tiempo real para usuarios'),

-- ============================================================
-- FINANCIAL
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
-- USER_ACQUISITION
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
-- USER_PROFILES
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
-- ADMINISTRATION
-- ============================================================

('ADMIN_PANEL',
 '/api/admin',
 'ENABLED',
 'ADMINISTRATION',
 'Panel de administración general')

ON DUPLICATE KEY UPDATE
                     status      = VALUES(status),
                     category    = VALUES(category),
                     description = VALUES(description);