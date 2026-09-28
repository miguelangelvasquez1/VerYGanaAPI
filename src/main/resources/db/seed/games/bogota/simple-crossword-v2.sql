-- simple-crossword — versión 2 del esquema de configuración.
--
-- Traduce al español el bloque de contenido y reescribe las ayudas para que las lea
-- un anunciante, no un desarrollador. Desde que existe el paso de contenido en la
-- solicitud, ese formulario lo llena la marca.
--
-- El resto del esquema —branding, audio, rewards— sigue igual: eso solo lo ve el
-- diseñador.
--
-- Va como versión nueva porque los seeds usan `INSERT ... WHERE NOT EXISTS`: editar
-- el archivo no toca ninguna base ya sembrada.

-- Los dos statements se guardan con el `url` además del id: los ids del seed son
-- fijos, pero en una base sembrada de otra forma el id podría ser de otro juego, y
-- este script le cambiaría el esquema sin error.
UPDATE game_config_definitions SET is_latest = false
WHERE game_id = 15 AND version = 1
  AND EXISTS (SELECT 1 FROM games WHERE id = 15 AND url = 'simple-crossword');

INSERT INTO game_config_definitions (
    game_id,
    version,
    json_schema,
    ui_schema,
    active,
    is_latest,
    created_at,
    created_by,
    average_reward_per_session_cents,
    completion_reward_cents,
    max_reward_per_session_cents,
    score_reward_factor,
    average_duration_seconds
)
SELECT
    15,
    2,
    '{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "title": "Crossword / Clue Word Game Configuration",
  "description": "Full configuration for a clue-based word game (e.g. crossword or word-clue matcher)",
  "required": [
    "meta",
    "branding",
    "game_config",
    "game",
    "audio",
    "texts",
    "rewards"
  ],
  "properties": {
    "meta": {
      "type": "object",
      "title": "Meta",
      "description": "Metadata identifying the brand and campaign",
      "required": [
        "brand_id",
        "campaign_id"
      ],
      "properties": {
        "brand_id": {
          "type": "string",
          "title": "Brand ID",
          "description": "Identifier for the brand configuration",
          "default": "",
          "minLength": 0,
          "maxLength": 64
        },
        "campaign_id": {
          "type": "string",
          "title": "Campaign ID",
          "description": "Identifier for the marketing campaign this game belongs to",
          "default": "",
          "minLength": 0,
          "maxLength": 64
        }
      }
    },
    "branding": {
      "type": "object",
      "title": "Branding",
      "description": "Logo and watermark image assets",
      "required": [
        "main_logo_url",
        "watermark_logo_url"
      ],
      "properties": {
        "main_logo_url": {
          "type": [
            "string",
            "null"
          ],
          "title": "Main Logo URL",
          "description": "URL of the main game logo image",
          "format": "uri",
          "default": ""
        },
        "watermark_logo_url": {
          "type": [
            "string",
            "null"
          ],
          "title": "Watermark Logo URL",
          "description": "URL of the small watermark logo overlaid on the game",
          "format": "uri",
          "default": ""
        }
      }
    },
    "game_config": {
      "type": "object",
      "title": "Game Config",
      "description": "Core gameplay parameters for the word-clue session",
      "required": [],
      "properties": {}
    },
    "game": {
      "type": "object",
      "title": "Contenido del juego",
      "description": "Lo que escribe la marca: el diseñador no puede inventarlo.",
      "required": [
        "entries"
      ],
      "properties": {
        "entries": {
          "type": "array",
          "title": "Palabras y pistas",
          "description": "Cada palabra del crucigrama con la pista que ve el jugador.",
          "minItems": 1,
          "items": {
            "type": "object",
            "title": "Entry",
            "description": "A single word paired with its corresponding clue",
            "required": [
              "word",
              "clue"
            ],
            "properties": {
              "word": {
                "type": "string",
                "title": "Palabra",
                "description": "La respuesta. Sin espacios ni signos.",
                "default": "",
                "minLength": 1,
                "maxLength": 64
              },
              "clue": {
                "type": "string",
                "title": "Pista",
                "description": "La definición que lee el jugador para adivinarla",
                "default": "",
                "minLength": 1,
                "maxLength": 512
              }
            }
          }
        }
      }
    },
    "audio": {
      "type": "object",
      "title": "Audio",
      "description": "URLs for all game audio assets",
      "required": [
        "key_win_url",
        "victory_url"
      ],
      "properties": {
        "key_win_url": {
          "type": [
            "string",
            "null"
          ],
          "title": "Key Win Sound URL",
          "description": "URL of the sound played when the player earns a key",
          "format": "uri",
          "default": ""
        },
        "victory_url": {
          "type": [
            "string",
            "null"
          ],
          "title": "Victory Sound URL",
          "description": "URL of the sound played when all entries are solved",
          "format": "uri",
          "default": ""
        }
      }
    },
    "texts": {
      "type": "object",
      "title": "Texts",
      "description": "Display text used throughout the game UI",
      "required": [
        "victory_messages"
      ],
      "properties": {
        "victory_messages": {
          "type": "array",
          "title": "Victory Messages",
          "description": "List of messages shown randomly or in sequence on the victory screen",
          "items": {
            "type": "string",
            "title": "Message",
            "description": "A single victory message string",
            "minLength": 0,
            "maxLength": 256
          }
        }
      }
    },
    "rewards": {
      "type": "object",
      "title": "Rewards",
      "description": "Key reward values for player actions",
      "required": [
        "keys_per_action",
        "keys_on_completion"
      ],
      "properties": {
        "keys_per_action": {
          "type": "integer",
          "title": "Keys Per Action",
          "description": "Keys awarded each time the player solves a word entry",
          "default": 0,
          "minimum": 0,
          "maximum": 10000
        },
        "keys_on_completion": {
          "type": "integer",
          "title": "Keys on Completion",
          "description": "Keys awarded upon solving all entries",
          "default": 0,
          "minimum": 0,
          "maximum": 100000
        }
      }
    }
  }
}',
    '{
  "ui:order": [
    "meta",
    "branding",
    "game_config",
    "game",
    "audio",
    "texts",
    "rewards"
  ],
  "meta": {
    "ui:title": "Meta",
    "ui:description": "Brand and campaign identifiers",
    "brand_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. my-brand",
      "ui:help": "Unique identifier for the brand configuration"
    },
    "campaign_id": {
      "ui:widget": "textInput",
      "ui:placeholder": "e.g. summer-2025",
      "ui:help": "Identifier for the campaign this game is associated with"
    }
  },
  "branding": {
    "ui:title": "Branding",
    "ui:description": "Logo and watermark assets",
    "ui:order": [
      "main_logo_url",
      "watermark_logo_url"
    ],
    "main_logo_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "image"
      },
      "ui:help": "Main logo displayed on the game screen"
    },
    "watermark_logo_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "image"
      },
      "ui:help": "Small watermark logo overlaid on the game"
    }
  },
  "game_config": {
    "ui:title": "Game Configuration",
    "ui:description": "Core gameplay parameters (no fields configured yet)"
  },
  "game": {
    "ui:title": "Contenido del juego",
    "ui:description": "Lo que escribe la marca",
    "entries": {
      "ui:title": "Palabras y pistas",
      "ui:description": "Agregá una entrada por palabra.",
      "items": {
        "ui:order": [
          "word",
          "clue"
        ],
        "word": {
          "ui:widget": "textInput",
          "ui:placeholder": "CHOCOLATE",
          "ui:help": "La respuesta, sin espacios ni signos"
        },
        "clue": {
          "ui:widget": "textInput",
          "ui:placeholder": "Dulce hecho de cacao",
          "ui:help": "La definición que lee el jugador"
        }
      }
    }
  },
  "audio": {
    "ui:title": "Audio",
    "ui:description": "Sound effects for key events",
    "ui:order": [
      "key_win_url",
      "victory_url"
    ],
    "key_win_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      },
      "ui:help": "Sound played when the player earns a key"
    },
    "victory_url": {
      "ui:widget": "assetUpload",
      "ui:options": {
        "assetType": "audio"
      },
      "ui:help": "Sound played when all entries have been solved"
    }
  },
  "texts": {
    "ui:title": "UI Texts",
    "ui:description": "Messages displayed on the victory screen",
    "victory_messages": {
      "ui:title": "Victory Messages",
      "ui:description": "Add one or more messages to display when the player wins",
      "items": {
        "ui:widget": "textInput",
        "ui:placeholder": "e.g. ¡Resolviste todas las pistas!",
        "ui:help": "A message shown on the victory screen"
      }
    }
  },
  "rewards": {
    "ui:title": "Rewards",
    "ui:description": "Key reward values for player progression",
    "keys_per_action": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted each time the player solves a word entry"
    },
    "keys_on_completion": {
      "ui:widget": "numberInput",
      "ui:placeholder": "0",
      "ui:help": "Keys granted upon solving all entries"
    }
  }
}',
    true,
    true,
    NOW(),
    'system',
    15000,
    5000,
    20000,
    1,
    60
WHERE NOT EXISTS (SELECT 1 FROM game_config_definitions WHERE game_id = 15 AND version = 2)
  AND EXISTS (SELECT 1 FROM games WHERE id = 15 AND url = 'simple-crossword');
