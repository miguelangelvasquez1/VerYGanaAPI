-- Casos PQRS y notificaciones de bandeja para los consumidores congelados
-- en beta_users.sql. Este seed requiere las compras del marketplace beta.
--
-- Los PQRS abiertos se asignan al admin beta para que aparezcan en
-- GET /admin/pqrs; los resueltos tienen respuesta y fecha de resolución.
-- Las notificaciones son IN_APP_NOTIFICATION y se muestran en la bandeja
-- individual del consumidor.
--
-- Idempotencia: requester + subject para PQRS y user + title para notificaciones.
-- Para borrar: primero notifications y pqrs_assets; luego pqrs.

INSERT INTO pqrs (
    action,
    created_at,
    description,
    due_date,
    reason_code,
    response,
    resolved_at,
    status,
    subject,
    type,
    updated_at,
    assigned_admin_id,
    purchase_item_id,
    requester_id
)
SELECT
    seed.action,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    seed.description,
    DATE_ADD(NOW(), INTERVAL seed.due_in_days DAY),
    seed.reason_code,
    seed.response,
    CASE
        WHEN seed.pqrs_status IN ('RESUELTA', 'CERRADA')
        THEN DATE_ADD(DATE_SUB(NOW(), INTERVAL seed.days_ago DAY), INTERVAL 1 DAY)
        ELSE NULL
    END,
    seed.pqrs_status,
    seed.subject,
    seed.pqrs_type,
    DATE_SUB(NOW(), INTERVAL seed.days_ago DAY),
    admin_details.user_id,
    purchase_item.id,
    consumer_user.id
FROM (
    SELECT
        'Consulta sobre el saldo de llaves' AS subject,
        'PETICION' AS pqrs_type,
        'RECIBIDA' AS pqrs_status,
        'Quisiera confirmar cómo se distribuyen las llaves recibidas entre saldo de compra y conectividad.' AS description,
        NULL AS response,
        NULL AS action,
        NULL AS reason_code,
        NULL AS purchase_reference,
        '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id,
        2 AS days_ago,
        5 AS due_in_days
    UNION ALL SELECT
        'Seguimiento a mi compra física',
        'RECLAMO',
        'EN_REVISION',
        'Realicé una compra de demostración y quisiera conocer el estado del reclamo del producto físico.',
        NULL,
        NULL,
        'NOT_DELIVERED',
        'BETA-MARKETPLACE-PHYSICAL-001',
        '0be7a000-0005-0000-0000-000000000005',
        1,
        6
    UNION ALL SELECT
        'Duda sobre una encuesta completada',
        'SUGERENCIA',
        'RESUELTA',
        'La encuesta aparece como completada y quería confirmar cuándo se refleja su recompensa en el historial.',
        'La recompensa de la encuesta de demostración ya está registrada en el historial de llaves de esta cuenta.',
        NULL,
        NULL,
        NULL,
        '0be7a000-0005-0000-0000-000000000008',
        8,
        -6
    UNION ALL SELECT
        'Consulta sobre el bono digital',
        'PETICION',
        'CERRADA',
        'Solicité información sobre la entrega del bono digital de la compra de demostración.',
        'El bono digital de demostración figura como reclamado en el historial de compras.',
        'DISMISS',
        NULL,
        'BETA-MARKETPLACE-DIGITAL-001',
        '0be7a000-0005-0000-0000-000000000004',
        12,
        -10
) seed
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN users admin_user
  ON admin_user.public_id = UUID_TO_BIN('0be7a000-0001-0000-0000-000000000001')
JOIN admin_details
  ON admin_details.user_id = admin_user.id
LEFT JOIN purchases purchase
  ON purchase.reference_id = seed.purchase_reference
LEFT JOIN purchase_items purchase_item
  ON purchase_item.purchase_id = purchase.id
WHERE NOT EXISTS (
    SELECT 1
    FROM pqrs existing_pqrs
    WHERE existing_pqrs.requester_id = consumer_user.id
      AND existing_pqrs.subject = seed.subject
);

INSERT INTO notifications (
    created_at,
    date_sent,
    is_read,
    message,
    title,
    type,
    user_id
)
SELECT
    DATE_SUB(NOW(), INTERVAL seed.minutes_ago MINUTE),
    DATE_SUB(NOW(), INTERVAL seed.minutes_ago MINUTE),
    seed.is_read,
    seed.message,
    seed.title,
    'IN_APP_NOTIFICATION',
    user_details.user_id
FROM (
    SELECT
        'Tu saldo de llaves está listo' AS title,
        'Ya puedes consultar el saldo y el detalle de tus llaves de demostración.' AS message,
        '0be7a000-0005-0000-0000-000000000004' AS consumer_public_id,
        120 AS minutes_ago,
        FALSE AS is_read
    UNION ALL SELECT
        'Actualización de tu compra',
        'Tu compra física de demostración aparece pendiente de reclamo en el marketplace.',
        '0be7a000-0005-0000-0000-000000000005',
        95,
        FALSE
    UNION ALL SELECT
        'Nueva encuesta disponible',
        'Hay encuestas de demostración para consultar desde tu cuenta.',
        '0be7a000-0005-0000-0000-000000000006',
        70,
        FALSE
    UNION ALL SELECT
        'Tu solicitud fue atendida',
        'La respuesta a tu solicitud sobre la recompensa de encuesta ya está disponible.',
        '0be7a000-0005-0000-0000-000000000008',
        45,
        FALSE
    UNION ALL SELECT
        'Conoce los sorteos beta',
        'Ya puedes revisar los sorteos disponibles y tus participaciones de demostración.',
        '0be7a000-0005-0000-0000-000000000009',
        25,
        FALSE
) seed
JOIN users consumer_user
  ON consumer_user.public_id = UUID_TO_BIN(seed.consumer_public_id)
JOIN user_details
  ON user_details.user_id = consumer_user.id
WHERE NOT EXISTS (
    SELECT 1
    FROM notifications existing_notification
    WHERE existing_notification.user_id = user_details.user_id
      AND existing_notification.title = seed.title
);
