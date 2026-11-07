INSERT INTO pricing_configs (
    id,
    version,
    type,
    amount_in_cents,
    currency,
    active,
    description,
    created_at
)
VALUES
    (
        1,
        1,
        'SURVEY_REWARD_PER_QUESTION_CENTS',
        2500, -- $25.00 COP por pregunta respondida
        'COP',
        true,
        'Recompensa en centavos que recibe el usuario por cada pregunta respondida en una encuesta',
        NOW()
    ),
    (
        2,
        1,
        'GAME_COST_PER_POINT_CENTS',
        5, -- $0.05 COP por punto
        'COP',
        true,
        'Costo en centavos que se descuenta al comercial por cada punto que gana un usuario en sus juegos',
        NOW()
    ),
    (
        3,
        1,
        'GAME_COST_PER_VICTORY_CENTS',
        500, -- $5.00 COP por victoria
        'COP',
        true,
        'Costo en centavos que se descuenta al comercial cuando un usuario completa o gana una partida',
        NOW()
    ),
    (
        4,
        1,
        'AD_COST_PER_SECOND_CENTS',
        250, -- $2.50 COP por segundo
        'COP',
        true,
        'Costo en centavos que se cobra al comercial por cada segundo de anuncio reproducido por un usuario',
        NOW()
    )

ON DUPLICATE KEY UPDATE
    amount_in_cents = VALUES(amount_in_cents),
    currency = VALUES(currency),
    active = VALUES(active),
    version = VALUES(version),
    description = VALUES(description);