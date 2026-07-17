-- Versiones iniciales (placeholder) de los documentos legales.
-- object_key es un placeholder (el archivo no existe realmente en R2 todavía) —
-- reemplazar subiendo el PDF real vía el flujo de URL pre-firmada
-- (POST /admin/legal-documents/prepare-upload -> ... -> /confirm), lo que
-- desactiva automáticamente la fila sembrada aquí.
INSERT INTO legal_documents (
    id,
    type,
    version,
    document_url,
    object_key,
    status,
    published_date,
    active,
    created_at
)
VALUES
    (
        1,
        'USERS_TERMS_AND_CONDITIONS',
        '1',
        'https://cdn.verygana.com/public/legal/users_terms_and_conditions/2-08c6da44-b309-41c6-8fdf-75a5806cb117.pdf',
        'legal/users_terms_and_conditions/2-08c6da44-b309-41c6-8fdf-75a5806cb117.pdf',
        'VALIDATED',
        '2026-01-01',
        true,
        NOW()
    ),
    (
        2,
        'BUSINESS_OWNER_TERMS_AND_CONDITIONS',
        '1',
        'https://cdn.verygana.com/public/legal/business_owner_terms_and_conditions/2-0fe5d398-36d0-4dc3-9331-ea2ba2818ccc.pdf',
        'legal/business_owner_terms_and_conditions/2-0fe5d398-36d0-4dc3-9331-ea2ba2818ccc.pdf',
        'VALIDATED',
        '2026-01-01',
        true,
        NOW()
    ),
    (
        3,
        'PRIVACY_POLICY',
        '1',
        'https://cdn.verygana.com/public/legal/privacy_policy/1.0-seed.pdf',
        'legal/privacy_policy/1.0-seed.pdf',
        'VALIDATED',
        '2026-01-01',
        true,
        NOW()
    ),
    (
        4,
        'DATA_PROCESSING_POLICY',
        '1',
        'https://cdn.verygana.com/public/legal/data_processing_policy/1.0-seed.pdf',
        'legal/data_processing_policy/1.0-seed.pdf',
        'VALIDATED',
        '2026-01-01',
        true,
        NOW()
    ),
    (
        5,
        'COOKIES_POLICY',
        '1',
        'https://cdn.verygana.com/public/legal/cookies_policy/1.0-seed.pdf',
        'legal/cookies_policy/1.0-seed.pdf',
        'VALIDATED',
        '2026-01-01',
        true,
        NOW()
    )

ON DUPLICATE KEY UPDATE
    type = VALUES(type),
    version = VALUES(version),
    document_url = VALUES(document_url),
    object_key = VALUES(object_key),
    status = VALUES(status),
    published_date = VALUES(published_date),
    active = VALUES(active);
